# HLSL Language Support for CLion / Rider

Syntax highlighting, code insight, DXC validation and clang-format for HLSL (High Level Shading Language) files.

![Example](example.png)

## Features

### Editing

- **Syntax highlighting** for keywords, types, built-in functions, semantics, preprocessor directives, numbers, strings, operators and comments
- **Struct/class name highlighting** — `struct`, `cbuffer`, `tbuffer`, `class`, `interface`, `enum` names and `typedef` aliases are highlighted at the declaration and at every usage site, including names declared in transitively `#include`d files
- **Code folding** — collapse any multi-line `{ ... }` block (functions, structs, cbuffers, control flow) and block comments, each region independent
- **Line and block commenting** (`Ctrl+/`, `Ctrl+Shift+/`)
- **Brace matching** for `()`, `{}`, `[]`
- **Color settings page** — customize every highlight color under *Settings → Editor → Color Scheme → HLSL*

### Code insight

- **Completion** that understands HLSL instead of guessing:
  - locals, globals, functions, structs, macros and `const` symbols from the current file **and its transitive `#include` chain**
  - **member completion** through `ConstantBuffer<T>` and plain struct variables — `gPass.` lists the fields of `T` even when `T` is declared in an included file
  - **intrinsic methods of resource types** — `Texture2D` (`Sample`, `SampleLevel`, `Load`, `GetDimensions`, `Gather*`, `CalculateLevelOfDetail*`, `.mips`), `StructuredBuffer`/`Buffer`, `ByteAddressBuffer` and the writable variants, including `Interlocked*`
  - **case-insensitive prefix matching** — `gPass.inv` offers `InvViewProj`, `tex.getdim` offers `GetDimensions` (HLSL naming is CamelCase; matching must not depend on the IDE's case-sensitivity setting)
  - **method call chaining** — `gGBuffer0.Sample(s, uv).rgb` resolves through the return type
  - semantics after `:`, built-in functions with auto-inserted parentheses
  - **no completion while you are naming a declaration** — typing `int2 pixel` or `out MaterialConstants mat` does not pop up the global vocabulary
- **Live templates** — see the table below
- **Go to declaration** (`Ctrl+Click` / `Ctrl+B`) — jump to function, variable and type declarations in the current file or any included file

### Live templates

Type the abbreviation and press `Tab`:

| Abbreviation | Expands to |
|---|---|
| `struct` | `struct Name { … };` |
| `cbuffer` | `cbuffer Name : register(b0) { … };` |

The `struct`/`cbuffer` keyword is not offered as a second, duplicate completion entry: the template entry (with its description) is what the list shows, because the platform orders two entries with the same text without consulting the plugin.

### Validation

- **DXC compiler validation** — real-time errors and warnings from the DirectX Shader Compiler, with clang's explanatory `note:` lines merged into the error so `no matching function for call to 'X'` also tells you *why* (`no known conversion from 'uint' to 'Light' for 1st argument`)
- **"Add DXC pragmas" intention** — inserts a `#pragma hlsl profile` / `entry` / `hv` skeleton at the top of the file

### Formatting

- **clang-format integration** — Reformat Code routes HLSL files through your `clang-format` binary

## Supported File Extensions

`hlsl`, `hlsli` are registered out of the box.

Additional extensions (`fx`, `fxh`, `vsh`, `psh`, `gsh`, `csh`, …) can be added in *Settings → Editor → File Types → HLSL File*.

## Installation

1. Download the latest `.zip` from the [Releases](../../releases) page, or build it yourself (see [Building](#building))
2. *Settings → Plugins → ⚙ → Install Plugin from Disk…*
3. Select the `.zip` and restart the IDE

## DXC validation

### Setup

1. Open *Settings → Tools → HLSL / DXC*
2. `dxc.exe` is auto-detected from `PATH`, the Windows SDK and the Vulkan SDK; set the path manually if yours lives elsewhere
3. Defaults: shader profile `ps_6_6`, entry point `main`, HLSL version `2021`

### Per-file overrides

```hlsl
// #pragma hlsl profile vs_6_6
// #pragma hlsl entry VSMain
// #pragma hlsl hv 2018
```

### Build macros — read this if you see phantom errors

A shader is normally compiled by your build system, which passes macros the plugin cannot see:

```cpp
const D3D_SHADER_MACRO lightDefines[] = {
    {"NUM_DIR_LIGHTS", "1"},
    {"FOG", "1"},
    {nullptr, nullptr}
};
```

A standalone `dxc` run without those macros reports `use of undeclared identifier 'NUM_DIR_LIGHTS'` for code that is correct in your build — and, worse, it **silently skips every `#ifdef`-guarded block**, which hides real errors inside them (or invents errors that only exist in the wrong configuration).

The plugin handles this in two ways:

- **Automatic detection (default on)** — it reads the `D3D_SHADER_MACRO` tables of your project and passes the macros that the shader mentions. Values that are runtime expressions (`extraLights.c_str()`), or that differ between passes, are never guessed and simply skipped. The scan is bounded and cached; when the IDE's project model does not expose the C++ sources (Rider/CLion keep their own model), the plugin falls back to the directory holding the `.sln`/`.vcxproj`/`CMakeLists.txt` above the shader.
- **Manual defines** — *Settings → Tools → HLSL / DXC → Preprocessor defines (-D)*, e.g. `NUM_DIR_LIGHTS=1;NUM_SPOT_LIGHTS=1`. These always win over detected macros.

The settings page shows what detection currently resolved (`Detected build macros: …`), which is the quickest way to confirm that a macro such as `FOG` really is defined for the compile.

### Noise policy

Validation is deliberately quiet so that real problems stand out:

- warnings use **DXC's own default set**; the extra groups (unused variables/parameters, unreachable code, uninitialized values, conversions, effects syntax) are **opt-in**, and clang's full `-Weverything` set is a separate toggle
- **diagnostics from include files are dropped** unless *Compile include files independently* is enabled — they never refer to a line of the file you are editing
- positionless compiler chatter (such as `unknown warning option`) is not reported
- at most five file-level annotations are shown, followed by a summary line

## clang-format

HLSL is not a language clang-format recognises by extension, so the IDE's built-in ClangFormat integration does not engage on `.hlsl` files. This plugin adds its own integration that routes Reformat Code through clang-format.

1. *Settings → Tools → HLSL / clang-format*
2. `clang-format` is auto-detected from `PATH`; the path and a fallback style can be set manually
3. Run Reformat Code (`Ctrl+Alt+L`) on an `.hlsl` file

Your `.clang-format` is discovered by walking up from the file's directory. Range formatting (reformat selection) is supported.

## Settings reference

*Settings → Tools → HLSL / DXC*

| Setting | Default | Notes |
|---|---|---|
| DXC executable path | auto-detect | `PATH` → Windows SDK → Vulkan SDK |
| Default shader profile | `ps_6_6` | overridden by `#pragma hlsl profile` |
| Default entry point | `main` | overridden by `#pragma hlsl entry` |
| HLSL version (`-HV`) | `2021` | overridden by `#pragma hlsl hv` |
| Preprocessor defines (`-D`) | empty | `NAME=VALUE`, separated by `;`, `,` or spaces; wins over detection |
| Detect build macros from the project | on | reads `D3D_SHADER_MACRO` tables |
| Enable DXC validation | on | |
| Compile include files independently | off | also validates `.hlsli`, `.fxh`, `.ush`, `.h`, `.inc` |
| Treat warnings as errors (`-WX`) | off | |
| Enable clang's full warning set (`-Weverything`) | off | noisy; expect warnings on correct HLSL |
| Extra warning groups | all off | unused variables/parameters, unreachable code, uninitialized values, conversions, effects syntax |
| Additional DXC arguments | empty | e.g. `-I "C:\Shader Includes"` |

## Building

Requires JDK 17 or newer (tested with JDK 21).

```
.\gradlew.bat buildPlugin
```

The plugin zip lands in `build/distributions/`.

## Tests

```
.\gradlew.bat test
```

87 tests cover the lexer, the declaration reader behind completion, the include index, DXC diagnostic parsing (including the real `no matching function` output) and IDE-level fixtures that run the actual completion contributor.

`src/test/resources/dx12/` is a verbatim snapshot of a real DX12 project's shaders used as fixtures. Point `HLSL_TEST_SOURCE_DIR` at a live shader folder to run the same checks against your own sources:

```
$env:HLSL_TEST_SOURCE_DIR = "D:\DX12\LearnDX12\HLSL"; .\gradlew.bat test
```

## Compatibility

- IntelliJ Platform 2024.1 – 2026.1 (`since-build` 241, `until-build` 261.*), built against IntelliJ Community 2024.1
- CLion and Rider, including projects where the C++ sources live outside the IDE's content roots
- Works alongside the C/C++ plugin

## Known gaps

- **Parameter info is not implemented** (`Ctrl+P` while typing an argument shows nothing) — the parameter list of the function you are calling is not displayed yet
- The editor-side analysis is intentionally light: no full PSI, no `Ctrl+Click` into a candidate of an overload set, no rename/refactor
- Type checking is delegated to DXC; the plugin does not re-implement HLSL semantics

## License

This project is licensed under the [MIT License](LICENSE).
