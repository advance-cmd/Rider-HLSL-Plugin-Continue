# HLSL Language Support for CLion / Rider

Completion, documentation, DXC validation and clang-format for HLSL files — including shaders that are
compiled by a C++ build system which passes its own macros.

## Features

### Writing code

- **Highlighting** for keywords, types, struct names, functions, variables, fields, semantics, numbers,
  strings and comments. Colours are built in, so a shader's own helpers and its variables are not plain
  text — see [Colours](#colours)
- **Struct names** are highlighted at the declaration and at every usage, including names that come from a
  transitively `#include`d file
- **Folding** of every `{ ... }` block and block comment, **`Ctrl+/`** comments, brace matching
- **Reformat** through your own `clang-format` — see [clang-format](#clang-format)

### Completion

- **Struct members through an include**: `gPass.` lists `ViewProj`, `InvViewProj`, `EyePosW`, `Lights`, …
  even though `PassConstants` is declared in `LightingUtil.hlsl` and `gPass` is a `ConstantBuffer<PassConstants>`
- **Resource intrinsics**: `gDepthMap.GetD` → `GetDimensions`; `Texture2D` also offers `Sample`,
  `SampleLevel`, `Load`, `Gather*`, `CalculateLevelOfDetail*` and `.mips`, buffers offer `Load`, byte address
  buffers `Load2`…`Load4`, writable ones `Interlocked*`
- **Chained calls**: `gGBuffer0.Sample(s, uv).` resolves the return type and offers `.rgb`, `.x`, …
- **Lowercase prefixes work**: `gPass.inv` offers `InvViewProj`; HLSL is CamelCase, so matching must not
  depend on the IDE's case-sensitivity setting
- Locals, globals, functions, structs, `#define` macros and globals declared `const`, from the file and its
  include chain; semantics after `:`; built-in functions with `()` inserted
- **Nothing pops up while you name a declaration**: typing `int2 pixel` or `out MaterialConstants mat` stays
  quiet instead of offering the global vocabulary

### Documentation

- **Hover** (or `Ctrl+Q`) on a function, variable, struct or macro shows its declaration with the comment
  written above it, resolved through the include chain:

  ```hlsl
  // 线性雾：距离 <= fogStart 不受影响，>= fogStart + fogRange 完全变成雾色，中间线性过渡。
  float3 ApplyFog(float3 color, float distToEye, float4 fogColor, float fogStart, float fogRange)
  ```

  Plain hover needs *Show quick documentation on hover* enabled in the IDE

- **Parameter info** opens by itself when you type `(` or `,`, and right after completing a function name:
  the parameter list with **the argument you are editing highlighted**, and `↑`/`↓` to step through
  overloads. `ApplyFog(` shows all five parameters, whichever file declares them

### Navigation

- **Go to declaration** (`Ctrl+Click` / `Ctrl+B`) for functions, variables and types in the current file or
  any included file

### Live templates

Type the abbreviation and press `Tab`:

| Abbreviation | Expands to |
|---|---|
| `struct` | `struct Name { … };` |
| `cbuffer` | `cbuffer Name : register(b0) { … };` |

`struct` is offered once, as the template: a second, identical keyword entry would only duplicate it.

### Validation (DXC)

- Errors and warnings from the DirectX Shader Compiler, inline as you type
- clang's explanation is attached to the error, so you get the reason and not just the verdict:

  ```
  no matching function for call to 'ApplyFog' — candidate function not viable:
  requires 5 arguments, but 0 were provided
  ```

## Colours

The plugin's colour keys carry their own values (a light and a dark one, picked by the active theme),
because the IDE colours neither function calls nor gives class names and local variables anything but the
text colour — which is why `ApplyFog`, variables and structs used to look unhighlighted while `saturate` was
coloured.

| Element | Dark | Light |
|---|---|---|
| Types, struct names (`float3`, `PassConstants`) | `#4EC9B0` | `#0E7C6B` |
| Functions, methods (`ApplyFog`) | `#FFC66D` | `#00627A` |
| Built-ins (`saturate`, `lerp`) | same as functions, *italic* | same as functions, *italic* |
| Members after a dot (`.EyePosW`, `.rgb`) | `#C77DBB` | `#871094` |
| Variables (`depth`, `gPass`) | `#9CDCFE` | `#1B5E9C` |
| Semantics (`SV_Target`, `TEXCOORD`) | `#B5CEA8` | `#9E880D` |

Keywords, numbers, strings and comments keep following your theme. Every entry can be changed or switched
back to *inherit* under **Settings → Editor → Color Scheme → HLSL**.

## DXC validation

### Setup

1. **Settings → Tools → HLSL / DXC**
2. `dxc.exe` is auto-detected from `PATH`, the Windows SDK and the Vulkan SDK; set the path manually if yours
   is elsewhere
3. Defaults: shader profile `ps_6_6`, entry point `main`, HLSL version `2021`

### Per-file overrides

```hlsl
// #pragma hlsl profile vs_6_6
// #pragma hlsl entry VSMain
// #pragma hlsl hv 2018
```

### Build macros

Your build system compiles the shader and passes macros that a standalone `dxc` run cannot see:

```cpp
const D3D_SHADER_MACRO lightDefines[] = {
    {"NUM_DIR_LIGHTS", "1"},
    {"FOG", "1"},
    {nullptr, nullptr}
};
```

Without them the plugin would report `use of undeclared identifier 'NUM_DIR_LIGHTS'` for code that is
correct, and would skip every `#ifdef`-guarded block.

- **Detection is on by default**: the plugin reads the `D3D_SHADER_MACRO` tables of your project and passes
  the macros the shader mentions. Values that are runtime expressions (`extraLights.c_str()`) or that differ
  between passes are never guessed, they are skipped. When the IDE's project model does not expose the C++
  sources, the scan falls back to the directory holding the `.sln`/`.vcxproj`/`CMakeLists.txt` above the
  shader (Rider and CLion keep their own model)
- **Manual defines win**: *Settings → Tools → HLSL / DXC → Preprocessor defines*, e.g.
  `NUM_DIR_LIGHTS=1;NUM_SPOT_LIGHTS=1`
- The settings page shows what detection resolved, which is the quickest way to confirm that a macro such as
  `FOG` really is defined for the compile

### Noise policy

Validation stays quiet so real problems stand out:

- warnings use DXC's own default set; the extra groups (unused variables/parameters, unreachable code,
  uninitialized values, conversions, effects syntax) are opt-in, and clang's full `-Weverything` set is a
  separate toggle
- diagnostics from include files are dropped unless *Compile include files independently* is enabled — their
  coordinates never refer to a line of the file you are editing
- positionless compiler chatter such as `unknown warning option` is not reported
- at most five file-level annotations are shown, followed by a summary line

## clang-format

HLSL is not a language clang-format recognises by extension, so the IDE's built-in ClangFormat integration
does not engage on `.hlsl` files. This plugin routes Reformat Code through clang-format instead.

1. **Settings → Tools → HLSL / clang-format**
2. `clang-format` is auto-detected from `PATH`; the path and a fallback style can be set manually
3. Run Reformat Code (`Ctrl+Alt+L`) on an `.hlsl` file

Your `.clang-format` is discovered by walking up from the file's directory, and range formatting works too.

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

## Supported file extensions

`hlsl` and `hlsli` are registered out of the box. Add more (`fx`, `fxh`, `vsh`, `psh`, `gsh`, `csh`, …)
under **Settings → Editor → File Types → HLSL File**.

## Installation

1. Download the latest `.zip` from the [Releases](../../releases) page, or build it yourself (see below)
2. **Settings → Plugins → ⚙ → Install Plugin from Disk…**
3. Select the `.zip` and restart the IDE

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

111 tests cover the lexer, the declaration reader behind completion, parameter info and hover
documentation, the include index, DXC diagnostic parsing (including the real `no matching function` output)
and IDE-level fixtures that run the actual completion contributor and parameter info handler.

`src/test/resources/dx12/` is a verbatim snapshot of a real DX12 project's shaders used as fixtures. Point
`HLSL_TEST_SOURCE_DIR` at a live shader folder to run the same checks against your own sources:

```
$env:HLSL_TEST_SOURCE_DIR = "D:\DX12\LearnDX12\HLSL"; .\gradlew.bat test
```

## Compatibility

- IntelliJ Platform 2024.1 – 2026.1 (`since-build` 241, `until-build` 261.*), built against IntelliJ
  Community 2024.1
- CLion and Rider, including projects whose C++ sources live outside the IDE's content roots
- Works alongside the C/C++ plugin, which may receive the same `.hlsl` files through the C++ project

## Known gaps

- The editor-side analysis is deliberately light: no full PSI, no `Ctrl+Click` into one candidate of an
  overload set, no rename or refactor
- Type checking is delegated to DXC; the plugin does not re-implement HLSL semantics

## License

This project is licensed under the [MIT License](LICENSE).
