# Third-Party Notices

This repository contains code that is derived from or structurally adapted
from third-party open-source projects.

## Anthropic self-hosted worker SDK

Portions of the self-hosted worker lifecycle, local agent tool, and client-side
MCP helper implementations under
`src/main/java/com/volcengine/ark/runtime/selfhosted` and `mcp`, including the
work poller, environment worker, session tool runner, skill initializer, local
tools, tool-result store, and MCP conversion helpers, are structurally adapted
from Anthropic's SDK implementations:

- https://github.com/anthropics/anthropic-sdk-go
- https://github.com/anthropics/anthropic-sdk-python
- https://github.com/anthropics/anthropic-sdk-java

The upstream projects are licensed under the MIT License. The MIT copyright
and permission notice is preserved below as required by that license.

```text
Copyright 2023 Anthropic, PBC.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
