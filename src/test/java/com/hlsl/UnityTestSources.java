package com.hlsl;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A small Unity project for the tests: a package shaped like Unity's Unified Ray Tracing API, downloaded into
 * Library/PackageCache under its name and a hash, and a .urtshader that includes it by its Packages/ path. The
 * sources are written for the tests and only reproduce the shape of the API: a namespace with constants,
 * structures with methods, a function that traces a ray, the macros that declare an acceleration structure,
 * and the ray generation shader Unity appends.
 */
public final class UnityTestSources {

    public static final String PACKAGE = "com.unity.render-pipelines.core";
    /** Where Unity keeps the downloaded package; the hash changes with every update. */
    public static final String CACHE_DIRECTORY = "Library/PackageCache/" + PACKAGE + "@1a2b3c4d5e6f/";
    public static final String URT = "Runtime/UnifiedRayTracing/";
    public static final String TRACE_INCLUDE = "Packages/" + PACKAGE + "/" + URT + "TraceRayAndQueryHit.hlsl";

    public static final String COMMON_STRUCTS = """
            #ifndef TEST_URT_COMMON_STRUCTS
            #define TEST_URT_COMMON_STRUCTS
            namespace UnifiedRT {

            static const uint kRayFlagNone = 0x0;
            static const uint kRayFlagForceOpaque = 0x01;
            static const uint kRayFlagAcceptFirstHitAndEndSearch = 0x04;

            struct Ray
            {
                float3 origin;
                float tMin;
                float3 direction;
                float tMax;
            };

            // What a ray hit; nothing when the instance ID is all ones
            struct Hit
            {
                uint instanceID;
                float hitDistance;

                bool IsValid()
                {
                    return instanceID != 0xFFFFFFFF;
                }

                static Hit Invalid()
                {
                    Hit hit = (Hit)0;
                    hit.instanceID = 0xFFFFFFFF;
                    return hit;
                }
            };

            struct DispatchInfo
            {
                uint3 dispatchThreadID;
            };

            struct RayTracingAccelStruct
            {
                RaytracingAccelerationStructure accelStruct;
            };

            } // namespace UnifiedRT
            #endif
            """;

    public static final String TRACE = """
            #ifndef TEST_URT_TRACE
            #define TEST_URT_TRACE
            #include "Packages/com.unity.render-pipelines.core/Runtime/UnifiedRayTracing/CommonStructs.hlsl"

            #define UNIFIED_RT_PAYLOAD UnifiedRT::Hit
            #define UNIFIED_RT_RAYGEN_FUNC RayGenExecute
            #define UNIFIED_RT_DECLARE_ACCEL_STRUCT(name) RaytracingAccelerationStructure name##accelStruct
            #define UNIFIED_RT_GET_ACCEL_STRUCT(name) UnifiedRT::GetAccelStruct(name##accelStruct)

            namespace UnifiedRT
            {
            RayTracingAccelStruct GetAccelStruct(RaytracingAccelerationStructure accelStruct)
            {
                RayTracingAccelStruct result;
                result.accelStruct = accelStruct;
                return result;
            }

            // Traces a ray and returns the closest hit
            Hit TraceRayClosestHit(DispatchInfo dispatchInfo, RayTracingAccelStruct accelStruct, uint instanceMask, Ray ray, uint rayFlags)
            {
                RayDesc desc;
                desc.Origin = ray.origin;
                desc.TMin = ray.tMin;
                desc.Direction = ray.direction;
                desc.TMax = ray.tMax;
                Hit payload = Hit::Invalid();
                TraceRay(accelStruct.accelStruct, rayFlags | kRayFlagForceOpaque, instanceMask, 0, 1, 0, desc, payload);
                return payload;
            }
            }
            #endif
            """;

    public static final String RAYGEN = """
            [shader("raygeneration")]
            void MainRayGenShader()
            {
                UnifiedRT::DispatchInfo dispatchInfo;
                dispatchInfo.dispatchThreadID = DispatchRaysIndex();
                UNIFIED_RT_RAYGEN_FUNC(dispatchInfo);
            }

            [shader("miss")]
            void MissShader(inout UNIFIED_RT_PAYLOAD payload : SV_RayPayload)
            {
            }
            """;

    /** A shader as a user writes it; the line of the trace call is {@link #TRACE_LINE}. */
    public static final String SHADER = """
            #include "Packages/com.unity.render-pipelines.core/Runtime/UnifiedRayTracing/TraceRayAndQueryHit.hlsl"

            UNIFIED_RT_DECLARE_ACCEL_STRUCT(_Scene);
            RWTexture2D<float4> _Output;

            UnifiedRT::Ray Forward(float3 origin)
            {
                UnifiedRT::Ray ray;
                ray.origin = origin;
                ray.direction = float3(0, 0, 1);
                ray.tMin = 0;
                ray.tMax = 1000;
                return ray;
            }

            void RayGenExecute(UnifiedRT::DispatchInfo dispatchInfo)
            {
                UnifiedRT::Hit hit = UnifiedRT::TraceRayClosestHit(dispatchInfo, UNIFIED_RT_GET_ACCEL_STRUCT(_Scene), 0xFF, Forward(0), UnifiedRT::kRayFlagNone);
                _Output[dispatchInfo.dispatchThreadID.xy] = hit.IsValid() ? hit.hitDistance : 0;
            }
            """;
    public static final int TRACE_LINE = 18;

    private UnityTestSources() {
    }

    /** Every file of the project, by its path relative to the project root. */
    public static Map<String, String> project(String shaderPath) {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("Packages/manifest.json", "{ \"dependencies\": {} }\n");
        files.put(CACHE_DIRECTORY + URT + "CommonStructs.hlsl", COMMON_STRUCTS);
        files.put(CACHE_DIRECTORY + URT + "TraceRayAndQueryHit.hlsl", TRACE);
        files.put(CACHE_DIRECTORY + URT + "Hardware/HardwareRaygenShader.hlsl", RAYGEN);
        files.put(shaderPath, SHADER);
        return files;
    }
}
