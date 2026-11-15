# Shared helpers of the EMS module scripts (ems-module-deps.py, ems-module-target.py).


def strip_comments(src):
    """Remove Java comments, keep every string, char and text-block literal as it is.

    A line comment is removed up to (not including) its newline; a block comment entirely.
    Literals stay, because the scripts count capitalized tokens in them on purpose.
    """
    out = []
    i, n = 0, len(src)
    while i < n:
        c = src[i]
        if src.startswith('"""', i):
            j = i + 3
            while j < n and not src.startswith('"""', j):
                j += 2 if src[j] == '\\' else 1
            j = min(j + 3, n)
            out.append(src[i:j]); i = j
        elif c == '"' or c == "'":
            j = i + 1
            while j < n and src[j] != c and src[j] != '\n':
                j += 2 if src[j] == '\\' else 1
            j = min(j + 1, n)
            out.append(src[i:j]); i = j
        elif src.startswith('//', i):
            j = src.find('\n', i)
            i = n if j < 0 else j
        elif src.startswith('/*', i):
            j = src.find('*/', i + 2)
            i = n if j < 0 else j + 2
        else:
            out.append(c); i += 1
    return ''.join(out)
