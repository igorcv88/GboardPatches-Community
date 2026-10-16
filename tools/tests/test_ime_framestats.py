import importlib.util
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('ime_stats',ROOT/'tools/analyze_ime_framestats.py')
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)

class ImeFramestatsTest(unittest.TestCase):
    def test_recorded_capture_is_not_double_counted_and_gpu_bucket_is_flagged(self):
        result=module.analyze((ROOT/'tools/performance/baselines/gboard-3.12.1-post-reset.txt').read_text())
        self.assertEqual(132,result['reported_frames'])
        self.assertEqual(85,result['reported_janky_frames'])
        self.assertEqual(120,result['csv_rows'])
        self.assertAlmostEqual(64.3939,result['reported_jank_percent'])
        self.assertTrue(result['gpu_4950ms_bucket_anomalous'])
        self.assertGreater(result['durations_ms']['QueueBufferDuration']['p95'],30)
        self.assertLess(result['durations_ms']['QueueBufferDuration']['p95'],31)
    def test_invalid_timestamps_do_not_create_negative_durations(self):
        sample='---PROFILEDATA---\nFlags,IntendedVsync,FrameCompleted,FrameStartTime,SyncStart,\n0,100,200,-1,10,\n0,200,100,300,200,\n---PROFILEDATA---'
        result=module.analyze(sample)
        self.assertEqual(1,result['durations_ms']['intended_to_completed']['n'])
        self.assertEqual(0,result['durations_ms']['frame_start_to_sync_start']['n'])
