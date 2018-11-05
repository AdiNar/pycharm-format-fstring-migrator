PyCharm plugin to replace string formats to fstrings. Just select roughly text containing formats (one or more),
press hotkey (Ctr+Alt+Q) and your fstring is ready!
Note that this is a heuristic, it should handle most cases, but you should take a look at the result (especially in multiline cases).

# Install
Just look for fstrings in PyCharm plugins.

# Usefulness
PyCharm has built-in support for such replacing available as code intention (I didn't know it while writing this code).
However, code intentions cannot be run for larger part of code at once. This plugin,
after small modifications, may be used to replace more formats at once or replace all 'obvious' formats with fstrings.

