# HLSL Language Support for CLion / Rider

Highlighting, completion, documentation, DXC validation and clang-format for HLSL files, aimed at shaders
that are compiled by a C++ build system which passes its own preprocessor macros.

## Features

### Writing code

- Keywords, types, struct names, functions, variables, fields, semantics, numbers, strings and comments
  are all coloured — including a shader's own helper functions and variables
- Struct names are coloured at the declaration and at every usage, even when the struct comes from an
  included file
- Folding for blocks and block comments, `Ctrl+/` line comments, brace matching
- Reformat Code through your own `clang-format`
- **Indentation while typing**: Enter inside a block indents the new line, and a closing brace written on its
  own line is put back at the column of the line that opened the block. A control statement written without
  braces (`if`, `for`, `while`) indents its body as if the braces were there. Braces inside comments and
  strings are not counted, and the indentation unit comes from the IDE's code style, so tabs stay tabs
- **Pasting keeps the block's shape**: a pasted block is moved to the indentation of the line it lands on
  instead of being indented on top of the indentation it already carried, so pasting the same block twice
  does not push it deeper every time

### Completion

- Members of a constant buffer, struct or included type, with access to members that are declared beyond
  the file being edited
- Resource intrinsics: sampling, level of detail, load and gather variants, mip levels, and the atomic
  operations of writable buffers
- Chained calls, so the members of a call's result are offered as well
- Case-insensitive matching, which matters because HLSL names are CamelCase
- Locals, globals, functions, structs, macros and `const` globals from the file and its include chain, and
  semantics after a colon
- Built-in functions are inserted with their parentheses
- The popup stays closed while a declaration is being written, so naming a new variable does not offer the
  global vocabulary

### Documentation

- Hover or `Ctrl+Q` on a function, variable, struct or macro shows its declaration together with the
  comment written above it, resolved through the include chain
- Plain hover needs *Show quick documentation on hover* enabled in the IDE
- **Semantics are explained on hover**: `SV_Target`, `SV_Position`, `TEXCOORD0`, `POSITION`, `register` and
  the interpolation modifiers (`linear`, `centroid`, `nointerpolation`, `noperspective`, `sample`) say what
  the pipeline does with them. The text comes in English and in Chinese, switchable under
  **Settings → Tools → HLSL / DXC**
- Parameter info opens by itself after a function name is completed and when `(` or `,` is typed, with the
  argument being edited highlighted and `↑`/`↓` to move through overloads

### Navigation

- Go to declaration (`Ctrl+Click` / `Ctrl+B`) for functions, variables and types, in the current file or in
  any included file

### Live templates

Press `Tab` after the abbreviation to expand it.

| Abbreviation | Expands to |
|---|---|
| `struct` | an empty struct definition |
| `cbuffer` | an empty constant buffer bound to a register |

### Validation (DXC)

- Errors and warnings from the DirectX Shader Compiler, inline as you type
- The compiler's explanation is attached to the message, so a failed call reports why it failed and not
  just that it did
- A diagnostic underlines the symbol it is about, not the rest of the line: the name is taken from the
  compiler's own message, so a mistake in an expression no longer paints a red line to the semicolon

## Colours

Function calls, struct names and local variables are plain text in the IDE, so the plugin's colour keys
carry their own values, one for light and one for dark themes.

| Element | Dark | Light |
|---|---|---|
| Types and struct names | `#4EC9B0` | `#0E7C6B` |
| Functions and methods | `#FFC66D` | `#00627A` |
| Built-in functions | as functions, *italic* | as functions, *italic* |
| Members after a dot | `#C77DBB` | `#871094` |
| Variables | `#9CDCFE` | `#1B5E9C` |
| Semantics | `#B5CEA8` | `#9E880D` |

Keywords, numbers, strings and comments follow your theme. Every entry can be changed or reset to
*inherit* under **Settings → Editor → Color Scheme → HLSL**.

## DXC validation

### Setup

1. **Settings → Tools → HLSL / DXC**
2. The executable is auto-detected from `PATH`, the Windows SDK and the Vulkan SDK, and can be set by hand
3. Defaults are shader profile `ps_6_6`, entry point `main`, HLSL version `2021`

Profile, entry point and HLSL version can be overridden per file, in a pragma comment.

### Build macros

A build system passes macros that a standalone compiler run does not see. Without them the plugin reports
undeclared identifiers for correct code, and skips every guarded block.

- Detection is on by default: the plugin reads the shader macro tables of the project and passes the macros
  the shader mentions, and never guesses values that are runtime expressions or that differ between passes.
  When the project model does not expose the C++ sources, the scan falls back to the directory holding the
  solution, project or CMake file above the shader
- Defines entered by hand in the settings page take precedence over detection
- The settings page shows what detection resolved, the quickest way to check that a macro really is defined
  for the compile

### Noise policy

Validation stays quiet so that real problems stand out.

- Warnings use the compiler's default set. The extra groups (unused variables and parameters, unreachable
  code, uninitialized values, conversions, effects syntax) and the full clang warning set are opt-in
- Diagnostics from include files are dropped unless *Compile include files independently* is enabled,
  because their coordinates do not refer to a line of the file being edited
- Positionless compiler chatter is not reported
- At most five file-level annotations are shown, followed by a summary line

## clang-format

The IDE's clang-format integration does not engage on these files, so Reformat Code is routed through
clang-format by the plugin.

1. **Settings → Tools → HLSL / clang-format**
2. The executable is auto-detected from `PATH`, and the path and a fallback style can be set by hand
3. Run Reformat Code (`Ctrl+Alt+L`) on an HLSL file

The configuration file is found by walking up from the file's directory, and range formatting works too.

## Settings reference

*Settings → Tools → HLSL / DXC*

| Setting | Default | Notes |
|---|---|---|
| DXC executable path | auto-detect | `PATH` → Windows SDK → Vulkan SDK |
| Default shader profile | `ps_6_6` | overridden by the per-file pragma |
| Default entry point | `main` | overridden by the per-file pragma |
| HLSL version | `2021` | overridden by the per-file pragma |
| Preprocessor defines | empty | `NAME=VALUE`, separated by `;`, `,` or spaces; wins over detection |
| Detect build macros from the project | on | reads the shader macro tables |
| Enable DXC validation | on | |
| Compile include files independently | off | also validates `.hlsli`, `.fxh`, `.ush`, `.h`, `.inc` |
| Treat warnings as errors | off | |
| Enable clang's full warning set | off | noisy; expect warnings on correct HLSL |
| Extra warning groups | all off | unused variables and parameters, unreachable code, uninitialized values, conversions, effects syntax |
| Additional DXC arguments | empty | include directories and similar |

## Supported file extensions

`hlsl` and `hlsli` are registered out of the box. Add more (`fx`, `fxh`, `vsh`, `psh`, `gsh`, `csh`, …)
under **Settings → Editor → File Types → HLSL File**.

## Installation

1. Download the latest `.zip` from the [Releases](../../releases) page, or build it yourself
2. **Settings → Plugins → ⚙ → Install Plugin from Disk…**
3. Select the `.zip` and restart the IDE

## Building

Requires JDK 17 or newer. Build the plugin with the Gradle `buildPlugin` task; the archive lands in
`build/distributions/`.

## Tests

Run the Gradle `test` task. The suite covers the lexer, the declaration reader behind completion, parameter
info and hover documentation, the include index, DXC diagnostic parsing, and IDE-level fixtures that run the
real completion contributor and parameter info handler.

Some tests check completion and documentation against the untrimmed shader sources of a real project. Those
sources are not in the repository, so the build skips the test classes that need them unless the sources are
provided: put them in `src/test/resources/dx12`, or point the `HLSL_TEST_SOURCE_DIR` environment variable at
a directory that holds them. With the sources in place the whole suite runs — 111 tests.

## Compatibility

- IntelliJ Platform 2024.1 – 2026.1 (`since-build` 241, `until-build` 261.*), built against IntelliJ
  Community 2024.1
- CLion and Rider, including projects whose C++ sources live outside the IDE's content roots
- Coexists with the C/C++ plugin, which may receive the same files through the C++ project

## Known gaps

- The editor-side analysis is deliberately light: no full PSI, no navigation into one candidate of an
  overload set, no rename or refactor
- Type checking is delegated to the compiler; HLSL semantics are not re-implemented

## License

This project is licensed under the [MIT License](LICENSE).
