package com.hlsl.completion;

import java.util.List;

/**
 * The attributes HLSL accepts in square brackets before an entry point or a statement, such as
 * {@code [numthreads(8, 8, 1)]} or {@code [unroll]}. They are neither keywords nor functions, so the general
 * vocabulary never offered them.
 */
final class HlslAttributes {

    /** What goes between the parentheses of an attribute, which decides what completing it inserts. */
    enum Arguments {
        /** No parentheses: {@code [unroll]}. */
        NONE,
        /** Numbers or names: {@code [numthreads(8, 8, 1)]}. */
        VALUES,
        /** One string: {@code [domain("tri")]}. */
        STRING
    }

    /** One attribute, with the argument list shown after its name and the stage or statement it applies to. */
    record Attribute(String name, Arguments arguments, String parameters, String appliesTo) { }

    static final List<Attribute> ALL = List.of(
            new Attribute("numthreads", Arguments.VALUES, "(x, y, z)", "compute, mesh, amplification"),
            new Attribute("WaveSize", Arguments.VALUES, "(lanes)", "compute"),
            new Attribute("shader", Arguments.STRING, "(\"stage\")", "library entry point"),
            new Attribute("RootSignature", Arguments.STRING, "(\"signature\")", "entry point"),
            new Attribute("earlydepthstencil", Arguments.NONE, "", "pixel"),
            new Attribute("maxvertexcount", Arguments.VALUES, "(count)", "geometry"),
            new Attribute("instance", Arguments.VALUES, "(count)", "geometry"),
            new Attribute("domain", Arguments.STRING, "(\"tri|quad|isoline\")", "hull, domain"),
            new Attribute("partitioning", Arguments.STRING,
                    "(\"integer|fractional_even|fractional_odd|pow2\")", "hull"),
            new Attribute("outputtopology", Arguments.STRING,
                    "(\"point|line|triangle|triangle_cw|triangle_ccw\")", "hull, mesh"),
            new Attribute("outputcontrolpoints", Arguments.VALUES, "(count)", "hull"),
            new Attribute("patchconstantfunc", Arguments.STRING, "(\"function\")", "hull"),
            new Attribute("maxtessfactor", Arguments.VALUES, "(factor)", "hull"),
            new Attribute("clipplanes", Arguments.VALUES, "(plane, ...)", "vertex"),
            new Attribute("unroll", Arguments.NONE, "", "loop"),
            new Attribute("loop", Arguments.NONE, "", "loop"),
            new Attribute("fastopt", Arguments.NONE, "", "loop"),
            new Attribute("allow_uav_condition", Arguments.NONE, "", "loop"),
            new Attribute("branch", Arguments.NONE, "", "if, switch"),
            new Attribute("flatten", Arguments.NONE, "", "if, switch"),
            new Attribute("forcecase", Arguments.NONE, "", "switch"),
            new Attribute("call", Arguments.NONE, "", "switch"));

    private HlslAttributes() {
    }
}
