#!/usr/bin/env python3
"""Optional exact-APK preflight for the 18.0.3 toolbar/pinned/search hooks.

Usage: python tools/validate_clipboard_ui_bindings.py original.apk (androguard).
No clipboard data or device access is used.
"""
import hashlib
import sys
import zipfile
from loguru import logger
logger.remove()
from androguard.core.dex import DEX

SHA256 = '3099262121a201dcb2ae7e185bee6b2057287a5aeffae4be0e30f537e505e5b3'
KEYBOARD = 'Lcom/google/android/apps/inputmethod/libs/clipboard/ClipboardKeyboard;'
METHODS = {
    'Leso;': {('b', '(Ljava/util/List;Lojt;Z)V'): 12},
    'Loup;': {('onStartInputView', '(Landroid/view/inputmethod/EditorInfo;Z)V'): 24},
    'Lcom/google/android/libraries/inputmethod/inputview/InputView;': {
        ('dispatchTouchEvent', '(Landroid/view/MotionEvent;)Z'): 7},
    'Lmln;': {('m', '(Lnur;)Z'): 14},
    'Lmlh;': {('M', '(Landroid/view/View;)V'): 3},
    'Lfjk;': {('p', '(Lkr;I)V'): 16, ('gT', '(I)I'): 2,
              ('I', '(Lfih;)V'): 19, ('L', '(Landroid/support/v7/widget/RecyclerView;)V'): 6,
              ('N', '()V'): 5},
    'Lfhz;': {('onClick', '(Landroid/view/View;)V'): None},
    'Lfjd;': {('onLongClick', '(Landroid/view/View;)Z'): None},
    'Lfjf;': {('m', '(Lkr;)V'): None},
    KEYBOARD: {('ao', '(Landroid/util/SparseArray;Z)V'): 9, ('J', '(I)V'): 4, ('K', '(I)V'): 6},
    'Lmic;': {('e', '(Lpmy;)Lcom/google/android/libraries/inputmethod/metadata/ActionDef;'): None},
    'Lnur;': {('d', '(Lpnu;)Lnur;'): None},
}


def main(path):
    with open(path, 'rb') as source:
        assert hashlib.file_digest(source, 'sha256').hexdigest() == SHA256, 'Unexpected APK'
    found = {}
    classes = {}
    with zipfile.ZipFile(path) as archive:
        for name in archive.namelist():
            if not name.endswith('.dex'):
                continue
            for cls in DEX(archive.read(name)).get_classes():
                if cls.get_name() not in METHODS:
                    continue
                classes[cls.get_name()] = cls
                for method in cls.get_methods():
                    key = (method.get_name(), method.get_descriptor().replace(' ', ''))
                    if key in METHODS[cls.get_name()]:
                        found[cls.get_name(), *key] = method
    for owner, expected in METHODS.items():
        for key, count in expected.items():
            method = found[owner, *key]
            if count is not None:
                assert method.get_code().get_registers_size() == count, (owner, key, count)
    for owner, name, signature, count in [
            ('Lfjk;', 'p', '(Lkr;I)V', 1),
            ('Lfhz;', 'onClick', '(Landroid/view/View;)V', 2),
            ('Lfjd;', 'onLongClick', '(Landroid/view/View;)Z', 1),
            ('Lfjf;', 'm', '(Lkr;)V', 1)]:
        refs = [i.get_output() for i in found[owner, name, signature].get_instructions()]
        assert sum('Lkr;->b()I' in ref for ref in refs) == count, owner
    dispatch = found['Lcom/google/android/libraries/inputmethod/inputview/InputView;',
                     'dispatchTouchEvent', '(Landroid/view/MotionEvent;)Z']
    refs = [i.get_output() for i in dispatch.get_instructions()]
    interceptor = next(i for i, ref in enumerate(refs) if 'Ljqy;->e(Landroid/view/View;' in ref)
    children = next(i for i, ref in enumerate(refs) if 'FrameLayout;->dispatchTouchEvent' in ref)
    assert interceptor < children, 'Expected the native interceptor before child dispatch'
    # mlh.M has three registers: p0=v1; G()'s result overwrites v1 before return.
    # An exit hook passing p0 therefore captures mln, whose h is boolean, not the catalog.
    capture = found['Lmlh;', 'M', '(Landroid/view/View;)V']
    refs = [i.get_output() for i in capture.get_instructions()]
    g = next(i for i, ref in enumerate(refs) if 'Lmlh;->G()Lmln;' in ref)
    assert refs[g + 1] == 'v1', 'Recheck controller receiver lifetime'
    fields = {(f.get_name(), f.get_descriptor()) for f in classes['Lmlh;'].get_fields()}
    assert ('h', 'Lays;') in fields
    for name, signature in [('ao', '(Landroid/util/SparseArray;Z)V')]:
        refs = [i.get_output() for i in found[KEYBOARD, name, signature].get_instructions()]
        assert sum('RecyclerView;->ak(I)V' in ref for ref in refs) == 1
    for name in ['J', 'K']:
        refs = [i.get_output() for i in found[KEYBOARD, name, '(I)V'].get_instructions()]
        assert sum('RecyclerView;->hU(I)Lkr;' in ref for ref in refs) == 1
    print('PASS: exact APK, toolbar candidate observer, native touch/input lifecycle, clipboard presentation/action/scroll boundaries and native action factory')


if __name__ == '__main__':
    main(sys.argv[1])
