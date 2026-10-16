#!/usr/bin/env python3
"""Analyze gfxinfo CSV without treating queue waits or anomalous GPU buckets as GPU work."""
import argparse
import csv
import hashlib
import io
import json
from pathlib import Path
import re


def percentile(values, p):
    values = sorted(values)
    if not values:
        return None
    index = (len(values)-1)*p/100
    low = int(index)
    return round(values[low] + (values[min(low+1,len(values)-1)]-values[low])*(index-low),4)


def analyze(text):
    frames=[]
    for block in text.split('---PROFILEDATA---')[1::2]:
        for row in csv.DictReader(io.StringIO(block.strip())):
            try:
                frame={k:int(v) for k,v in row.items() if k and v not in ('',None)}
                if 'IntendedVsync' in frame and 'FrameCompleted' in frame:
                    frames.append(frame)
            except (ValueError,TypeError):
                continue
    # gfxinfo repeats its human-readable aggregate per ViewRoot; use the first summary.
    def aggregate(pattern,cast=int):
        match=re.search(pattern,text)
        return cast(match[1]) if match else None
    rendered=aggregate(r'Total frames rendered: (\d+)')
    janky=aggregate(r'Janky frames: (\d+)')
    result={
        'reported_frames':rendered,'reported_janky_frames':janky,
        'reported_jank_percent':round(janky*100/rendered,4) if rendered else None,
        'reported_duration_percentiles_ms':{
            str(p):aggregate(rf'{p}(?:th|st|nd|rd) percentile: (\d+)ms') for p in (50,90,95,99)},
        'csv_rows':len(frames),
        'csv_normal_rows':sum(f.get('Flags')==0 for f in frames),
        'sha256':hashlib.sha256(text.encode()).hexdigest(),
        'gpu_4950ms_bucket_anomalous':bool(re.search(r'GPU HISTOGRAM:.*4950ms=[1-9]',text)),
        'durations_ms':{},
    }
    for label,start,end in [
        ('intended_to_completed','IntendedVsync','FrameCompleted'),
        ('frame_start_to_sync_start','FrameStartTime','SyncStart'),
        ('ui_until_sync_queued','FrameStartTime','SyncQueued'),
        ('issue_to_swap','IssueDrawCommandsStart','SwapBuffers'),
    ]:
        values=[(f[end]-f[start])/1e6 for f in frames
                if f.get(start,0)>0 and f.get(end,0)>=f[start]]
        result['durations_ms'][label]={'n':len(values),**{f'p{p}':percentile(values,p) for p in (50,90,95,99)}}
    for name in ['QueueBufferDuration','DequeueBufferDuration','FrameInterval']:
        values=[f[name]/1e6 for f in frames if f.get(name,0)>0]
        result['durations_ms'][name]={'n':len(values),**{f'p{p}':percentile(values,p) for p in (50,90,95,99)}}
    result['interpretation']='Queue and sync intervals include waiting/backpressure; they do not isolate GPU execution. Device A/B and Perfetto remain required.'
    return result


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('input',type=Path)
    parser.add_argument('--compare',type=Path)
    args=parser.parse_args()
    result=analyze(args.input.read_text())
    if args.compare:
        baseline=analyze(args.compare.read_text())
        result['comparison']={
            'baseline_sha256':baseline['sha256'],
            'jank_delta_percentage_points':(
                round(result['reported_jank_percent'] - baseline['reported_jank_percent'], 4)
                if result['reported_jank_percent'] is not None
                and baseline['reported_jank_percent'] is not None else None),
            'warning':'Only a controlled same-device/same-scenario capture supports a performance claim.'}
    print(json.dumps(result,indent=2))


if __name__=='__main__':
    main()
