template<typename T = float4>
class Texture1D
{
    T Sample(SamplerState s, float location);
    T SampleBias(SamplerState s, float location, float bias);
    T SampleCmp(SamplerComparisonState s, float location, float compareValue);
    T SampleCmpLevelZero(SamplerComparisonState s, float location, float compareValue);
    T SampleGrad(SamplerState s, float location, float ddx, float ddy);
    T SampleLevel(SamplerState s, float location, float lod);
    float CalculateLevelOfDetail(SamplerState s, float location);
    float CalculateLevelOfDetailUnclamped(SamplerState s, float location);
    T Load(int2 locationAndMip);
    void GetDimensions(out uint width);
    void GetDimensions(uint mipLevel, out uint width, out uint numberOfLevels);
    T operator[](uint location);
};

template<typename T = float4>
class Texture1DArray
{
    T Sample(SamplerState s, float2 locationAndSlice);
    T SampleBias(SamplerState s, float2 locationAndSlice, float bias);
    T SampleCmp(SamplerComparisonState s, float2 locationAndSlice, float compareValue);
    T SampleCmpLevelZero(SamplerComparisonState s, float2 locationAndSlice, float compareValue);
    T SampleGrad(SamplerState s, float2 locationAndSlice, float ddx, float ddy);
    T SampleLevel(SamplerState s, float2 locationAndSlice, float lod);
    float CalculateLevelOfDetail(SamplerState s, float location);
    float CalculateLevelOfDetailUnclamped(SamplerState s, float location);
    T Load(int3 locationSliceAndMip);
    void GetDimensions(out uint width, out uint elements);
    void GetDimensions(uint mipLevel, out uint width, out uint elements, out uint numberOfLevels);
    T operator[](uint2 locationAndSlice);
};

template<typename T = float4>
class Texture2D
{
    T Sample(SamplerState s, float2 location);
    T SampleBias(SamplerState s, float2 location, float bias);
    T SampleCmp(SamplerComparisonState s, float2 location, float compareValue);
    T SampleCmpLevelZero(SamplerComparisonState s, float2 location, float compareValue);
    T SampleGrad(SamplerState s, float2 location, float2 ddx, float2 ddy);
    T SampleLevel(SamplerState s, float2 location, float lod);
    float4 Gather(SamplerState s, float2 location);
    float4 GatherRed(SamplerState s, float2 location);
    float4 GatherGreen(SamplerState s, float2 location);
    float4 GatherBlue(SamplerState s, float2 location);
    float4 GatherAlpha(SamplerState s, float2 location);
    float4 GatherCmp(SamplerComparisonState s, float2 location, float compareValue);
    float CalculateLevelOfDetail(SamplerState s, float2 location);
    float CalculateLevelOfDetailUnclamped(SamplerState s, float2 location);
    T Load(int3 locationAndMip);
    void GetDimensions(out uint width, out uint height);
    void GetDimensions(uint mipLevel, out uint width, out uint height, out uint numberOfLevels);
    T operator[](uint2 location);
};

template<typename T = float4>
class Texture2DArray
{
    T Sample(SamplerState s, float3 locationAndSlice);
    T SampleBias(SamplerState s, float3 locationAndSlice, float bias);
    T SampleCmp(SamplerComparisonState s, float3 locationAndSlice, float compareValue);
    T SampleCmpLevelZero(SamplerComparisonState s, float3 locationAndSlice, float compareValue);
    T SampleGrad(SamplerState s, float3 locationAndSlice, float2 ddx, float2 ddy);
    T SampleLevel(SamplerState s, float3 locationAndSlice, float lod);
    float4 Gather(SamplerState s, float3 locationAndSlice);
    float4 GatherCmp(SamplerComparisonState s, float3 locationAndSlice, float compareValue);
    float CalculateLevelOfDetail(SamplerState s, float2 location);
    float CalculateLevelOfDetailUnclamped(SamplerState s, float2 location);
    T Load(int4 locationSliceAndMip);
    void GetDimensions(out uint width, out uint height, out uint elements);
    void GetDimensions(uint mipLevel, out uint width, out uint height, out uint elements, out uint numberOfLevels);
    T operator[](uint3 locationAndSlice);
};

template<typename T = float4>
class Texture3D
{
    T Sample(SamplerState s, float3 location);
    T SampleBias(SamplerState s, float3 location, float bias);
    T SampleGrad(SamplerState s, float3 location, float3 ddx, float3 ddy);
    T SampleLevel(SamplerState s, float3 location, float lod);
    float CalculateLevelOfDetail(SamplerState s, float3 location);
    float CalculateLevelOfDetailUnclamped(SamplerState s, float3 location);
    T Load(int4 locationAndMip);
    void GetDimensions(out uint width, out uint height, out uint depth);
    void GetDimensions(uint mipLevel, out uint width, out uint height, out uint depth, out uint numberOfLevels);
    T operator[](uint3 location);
};

template<typename T = float4>
class TextureCube
{
    T Sample(SamplerState s, float3 direction);
    T SampleBias(SamplerState s, float3 direction, float bias);
    T SampleCmp(SamplerComparisonState s, float3 direction, float compareValue);
    T SampleCmpLevelZero(SamplerComparisonState s, float3 direction, float compareValue);
    T SampleGrad(SamplerState s, float3 direction, float3 ddx, float3 ddy);
    T SampleLevel(SamplerState s, float3 direction, float lod);
    float4 Gather(SamplerState s, float3 direction);
    float4 GatherCmp(SamplerComparisonState s, float3 direction, float compareValue);
    float CalculateLevelOfDetail(SamplerState s, float3 direction);
    float CalculateLevelOfDetailUnclamped(SamplerState s, float3 direction);
    void GetDimensions(out uint width, out uint height);
    void GetDimensions(uint mipLevel, out uint width, out uint height, out uint numberOfLevels);
};

template<typename T = float4>
class TextureCubeArray
{
    T Sample(SamplerState s, float4 directionAndSlice);
    T SampleBias(SamplerState s, float4 directionAndSlice, float bias);
    T SampleCmp(SamplerComparisonState s, float4 directionAndSlice, float compareValue);
    T SampleCmpLevelZero(SamplerComparisonState s, float4 directionAndSlice, float compareValue);
    T SampleGrad(SamplerState s, float4 directionAndSlice, float3 ddx, float3 ddy);
    T SampleLevel(SamplerState s, float4 directionAndSlice, float lod);
    float4 Gather(SamplerState s, float4 directionAndSlice);
    float4 GatherCmp(SamplerComparisonState s, float4 directionAndSlice, float compareValue);
    float CalculateLevelOfDetail(SamplerState s, float3 direction);
    float CalculateLevelOfDetailUnclamped(SamplerState s, float3 direction);
    void GetDimensions(out uint width, out uint height, out uint elements);
    void GetDimensions(uint mipLevel, out uint width, out uint height, out uint elements, out uint numberOfLevels);
};

template<typename T = float4, int SampleCount = 0>
class Texture2DMS
{
    T Load(int2 location, int sampleIndex);
    float2 GetSamplePosition(int sampleIndex);
    void GetDimensions(out uint width, out uint height, out uint numberOfSamples);
    T operator[](uint2 location);
};

template<typename T = float4, int SampleCount = 0>
class Texture2DMSArray
{
    T Load(int3 locationAndSlice, int sampleIndex);
    float2 GetSamplePosition(int sampleIndex);
    void GetDimensions(out uint width, out uint height, out uint elements, out uint numberOfSamples);
    T operator[](uint3 locationAndSlice);
};

template<typename T = float4>
class RWTexture1D
{
    T Load(int location);
    void GetDimensions(out uint width);
    T operator[](uint location);
};

template<typename T = float4>
class RWTexture1DArray
{
    T Load(int2 locationAndSlice);
    void GetDimensions(out uint width, out uint elements);
    T operator[](uint2 locationAndSlice);
};

template<typename T = float4>
class RWTexture2D
{
    T Load(int2 location);
    void GetDimensions(out uint width, out uint height);
    T operator[](uint2 location);
};

template<typename T = float4>
class RWTexture2DArray
{
    T Load(int3 locationAndSlice);
    void GetDimensions(out uint width, out uint height, out uint elements);
    T operator[](uint3 locationAndSlice);
};

template<typename T = float4>
class RWTexture3D
{
    T Load(int3 location);
    void GetDimensions(out uint width, out uint height, out uint depth);
    T operator[](uint3 location);
};

template<typename T = float4>
class Buffer
{
    T Load(int location);
    void GetDimensions(out uint elements);
    T operator[](uint location);
};

template<typename T = float4>
class RWBuffer
{
    T Load(int location);
    void GetDimensions(out uint elements);
    T operator[](uint location);
};

template<typename T>
class StructuredBuffer
{
    T Load(int location);
    void GetDimensions(out uint numStructs, out uint stride);
    T operator[](uint location);
};

template<typename T>
class RWStructuredBuffer
{
    T Load(int location);
    uint IncrementCounter();
    uint DecrementCounter();
    void GetDimensions(out uint numStructs, out uint stride);
    T operator[](uint location);
};

template<typename T>
class AppendStructuredBuffer
{
    void Append(T value);
    void GetDimensions(out uint numStructs, out uint stride);
};

template<typename T>
class ConsumeStructuredBuffer
{
    T Consume();
    void GetDimensions(out uint numStructs, out uint stride);
};

class ByteAddressBuffer
{
    uint Load(uint address);
    uint2 Load2(uint address);
    uint3 Load3(uint address);
    uint4 Load4(uint address);
    template<typename T> T Load(uint address);
    void GetDimensions(out uint byteSize);
};

class RWByteAddressBuffer
{
    uint Load(uint address);
    uint2 Load2(uint address);
    uint3 Load3(uint address);
    uint4 Load4(uint address);
    template<typename T> T Load(uint address);
    void Store(uint address, uint value);
    void Store2(uint address, uint2 values);
    void Store3(uint address, uint3 values);
    void Store4(uint address, uint4 values);
    template<typename T> void Store(uint address, T value);
    void InterlockedAdd(uint address, uint value, out uint originalValue);
    void InterlockedAnd(uint address, uint value, out uint originalValue);
    void InterlockedOr(uint address, uint value, out uint originalValue);
    void InterlockedXor(uint address, uint value, out uint originalValue);
    void InterlockedMin(uint address, uint value, out uint originalValue);
    void InterlockedMax(uint address, uint value, out uint originalValue);
    void InterlockedExchange(uint address, uint value, out uint originalValue);
    void InterlockedCompareExchange(uint address, uint compareValue, uint value, out uint originalValue);
    void InterlockedCompareStore(uint address, uint compareValue, uint value);
    void GetDimensions(out uint byteSize);
};

template<typename T>
class ConstantBuffer
{
};

class SamplerState
{
};

class SamplerComparisonState
{
};

template<typename T, uint ControlPointCount>
class InputPatch
{
    uint Length;
    T operator[](uint index);
};

template<typename T, uint ControlPointCount>
class OutputPatch
{
    uint Length;
    T operator[](uint index);
};

template<typename T>
class PointStream
{
    void Append(T vertex);
    void RestartStrip();
};

template<typename T>
class LineStream
{
    void Append(T vertex);
    void RestartStrip();
};

template<typename T>
class TriangleStream
{
    void Append(T vertex);
    void RestartStrip();
};

class RaytracingAccelerationStructure
{
};

struct RayDesc
{
    float3 Origin;
    float TMin;
    float3 Direction;
    float TMax;
};

template<uint RayFlags>
class RayQuery
{
    void TraceRayInline(RaytracingAccelerationStructure accelerationStructure, uint rayFlags, uint instanceInclusionMask, RayDesc ray);
    bool Proceed();
    void Abort();
    uint CandidateType();
    uint CommittedStatus();
    void CommitNonOpaqueTriangleHit();
    void CommitProceduralPrimitiveHit(float tHit);
    float CommittedRayT();
    float2 CommittedTriangleBarycentrics();
    uint CommittedInstanceIndex();
    uint CommittedPrimitiveIndex();
    uint CommittedGeometryIndex();
    float CandidateTriangleRayT();
    float2 CandidateTriangleBarycentrics();
    float3 WorldRayOrigin();
    float3 WorldRayDirection();
    float RayTMin();
};

class ResourceDescriptorHeap
{
    Resource operator[](uint index);
};

class SamplerDescriptorHeap
{
    Sampler operator[](uint index);
};

template<typename T = float, int Size = 4>
class vector
{
};

template<typename T = float, int Rows = 4, int Columns = 4>
class matrix
{
};

class sampler
{
};

class sampler1D
{
};

class sampler2D
{
};

class sampler3D
{
};

class samplerCUBE
{
};

class texture
{
};

T abs(T x);

T acos(T x);

T asin(T x);

T atan(T x);

T atan2(T y, T x);

T cos(T x);

T cosh(T x);

T sin(T x);

T sinh(T x);

void sincos(T x, out T s, out T c);

T tan(T x);

T tanh(T x);

T degrees(T x);

T radians(T x);

T ceil(T x);

T floor(T x);

T round(T x);

T trunc(T x);

T frac(T x);

T fmod(T x, T y);

T modf(T x, out T integerPart);

T frexp(T x, out T exponent);

T ldexp(T x, T exponent);

T exp(T x);

T exp2(T x);

T log(T x);

T log10(T x);

T log2(T x);

T pow(T x, T y);

T sqrt(T x);

T rsqrt(T x);

T rcp(T x);

T min(T x, T y);

T max(T x, T y);

T clamp(T x, T min, T max);

T saturate(T x);

int sign(T x);

T lerp(T x, T y, T s);

T smoothstep(T min, T max, T x);

T step(T y, T x);

T mad(T m, T a, T b);

double fma(double a, double b, double c);

bool all(T x);

bool any(T x);

bool isfinite(T x);

bool isinf(T x);

bool isnan(T x);

float dot(T x, T y);

float3 cross(float3 x, float3 y);

float length(T x);

float distance(T x, T y);

T normalize(T x);

T reflect(T i, T n);

T refract(T i, T n, float eta);

T faceforward(T n, T i, T ng);

float4 mul(float4 v, float4x4 m);
float4 mul(float4x4 m, float4 v);
float4x4 mul(float4x4 a, float4x4 b);

float determinant(matrix m);

matrix<T, Columns, Rows> transpose(matrix<T, Rows, Columns> m);

float4 dst(float4 a, float4 b);

float4 lit(float nDotL, float nDotH, float m);

float noise(T x);

float asfloat(int x);
float asfloat(uint x);

int asint(float x);
int asint(uint x);

uint asuint(float x);
uint asuint(int x);
void asuint(double value, out uint lowBits, out uint highBits);

double asdouble(uint lowBits, uint highBits);

float f16tof32(uint value);

uint f32tof16(float value);

int4 D3DCOLORtoUBYTE4(float4 color);

uint countbits(uint value);

uint reversebits(uint value);

uint firstbithigh(uint value);
int firstbithigh(int value);

uint firstbitlow(uint value);

uint4 msad4(uint reference, uint2 source, uint4 accumulator);

void clip(T x);

T ddx(T x);

T ddx_coarse(T x);

T ddx_fine(T x);

T ddy(T x);

T ddy_coarse(T x);

T ddy_fine(T x);

T fwidth(T x);

T EvaluateAttributeAtCentroid(T value);

T EvaluateAttributeAtSample(T value, uint sampleIndex);

T EvaluateAttributeSnapped(T value, int2 offset);

uint GetRenderTargetSampleCount();

float2 GetRenderTargetSamplePosition(int index);

void AllMemoryBarrier();

void AllMemoryBarrierWithGroupSync();

void DeviceMemoryBarrier();

void DeviceMemoryBarrierWithGroupSync();

void GroupMemoryBarrier();

void GroupMemoryBarrierWithGroupSync();

void InterlockedAdd(inout T dest, T value);
void InterlockedAdd(inout T dest, T value, out T originalValue);

void InterlockedAnd(inout T dest, T value);
void InterlockedAnd(inout T dest, T value, out T originalValue);

void InterlockedOr(inout T dest, T value);
void InterlockedOr(inout T dest, T value, out T originalValue);

void InterlockedXor(inout T dest, T value);
void InterlockedXor(inout T dest, T value, out T originalValue);

void InterlockedMin(inout T dest, T value);
void InterlockedMin(inout T dest, T value, out T originalValue);

void InterlockedMax(inout T dest, T value);
void InterlockedMax(inout T dest, T value, out T originalValue);

void InterlockedExchange(inout T dest, T value, out T originalValue);

void InterlockedCompareExchange(inout T dest, T compareValue, T value, out T originalValue);

void InterlockedCompareStore(inout T dest, T compareValue, T value);

bool WaveIsFirstLane();

uint WaveGetLaneCount();

uint WaveGetLaneIndex();

bool WaveActiveAllEqual(T value);

uint WaveActiveCountBits(bool bit);

T WaveActiveSum(T value);

T WaveActiveProduct(T value);

T WaveActiveMin(T value);

T WaveActiveMax(T value);

T WaveActiveBitAnd(T value);

T WaveActiveBitOr(T value);

T WaveActiveBitXor(T value);

uint WavePrefixCountBits(bool bit);

T WavePrefixSum(T value);

T WavePrefixProduct(T value);

T WaveReadLaneAt(T value, uint laneIndex);

T WaveReadLaneFirst(T value);

void ProcessIsolineTessFactors(float rawDetailFactor, float rawDensityFactor, out float roundedDetailFactor, out float roundedDensityFactor);

void ProcessTriTessFactorsAvg(float3 rawEdgeFactors, float insideScale, out float3 roundedEdgeTessFactors, out float roundedInsideTessFactor, out float unroundedInsideTessFactor);

void ProcessTriTessFactorsMax(float3 rawEdgeFactors, float insideScale, out float3 roundedEdgeTessFactors, out float roundedInsideTessFactor, out float unroundedInsideTessFactor);

void ProcessTriTessFactorsMin(float3 rawEdgeFactors, float insideScale, out float3 roundedEdgeTessFactors, out float roundedInsideTessFactor, out float unroundedInsideTessFactor);

void ProcessQuadTessFactorsAvg(float4 rawEdgeFactors, float insideScale, out float4 roundedEdgeTessFactors, out float2 roundedInsideTessFactors, out float2 unroundedInsideTessFactors);

void ProcessQuadTessFactorsMax(float4 rawEdgeFactors, float insideScale, out float4 roundedEdgeTessFactors, out float2 roundedInsideTessFactors, out float2 unroundedInsideTessFactors);

void ProcessQuadTessFactorsMin(float4 rawEdgeFactors, float insideScale, out float4 roundedEdgeTessFactors, out float2 roundedInsideTessFactors, out float2 unroundedInsideTessFactors);

void Process2DQuadTessFactorsAvg(float4 rawEdgeFactors, float2 insideScale, out float4 roundedEdgeTessFactors, out float2 roundedInsideTessFactors, out float2 unroundedInsideTessFactors);

void Process2DQuadTessFactorsMax(float4 rawEdgeFactors, float2 insideScale, out float4 roundedEdgeTessFactors, out float2 roundedInsideTessFactors, out float2 unroundedInsideTessFactors);

void Process2DQuadTessFactorsMin(float4 rawEdgeFactors, float2 insideScale, out float4 roundedEdgeTessFactors, out float2 roundedInsideTessFactors, out float2 unroundedInsideTessFactors);

void TraceRay(RaytracingAccelerationStructure accelerationStructure, uint rayFlags, uint instanceInclusionMask, uint rayContributionToHitGroupIndex, uint multiplierForGeometryContributionToHitGroupIndex, uint missShaderIndex, RayDesc ray, inout Payload payload);

bool ReportHit(float tHit, uint hitKind, Attributes attributes);

void CallShader(uint shaderIndex, inout Parameter parameter);

void IgnoreHit();

void AcceptHitAndEndSearch();

uint3 DispatchRaysIndex();

uint3 DispatchRaysDimensions();

float3 WorldRayOrigin();

float3 WorldRayDirection();

float3 ObjectRayOrigin();

float3 ObjectRayDirection();

float RayTMin();

float RayTCurrent();

uint RayFlags();

uint InstanceIndex();

uint InstanceID();

uint PrimitiveIndex();

uint HitKind();

float3x4 ObjectToWorld3x4();

float4x3 ObjectToWorld4x3();

float3x4 WorldToObject3x4();

float4x3 WorldToObject4x3();

void printf(string format, ...);

float4 tex1D(sampler1D s, float t);

float4 tex1Dbias(sampler1D s, float4 t);

float4 tex1Dgrad(sampler1D s, float t, float ddx, float ddy);

float4 tex1Dlod(sampler1D s, float4 t);

float4 tex1Dproj(sampler1D s, float4 t);

float4 tex2D(sampler2D s, float2 t);

float4 tex2Dbias(sampler2D s, float4 t);

float4 tex2Dgrad(sampler2D s, float2 t, float2 ddx, float2 ddy);

float4 tex2Dlod(sampler2D s, float4 t);

float4 tex2Dproj(sampler2D s, float4 t);

float4 tex3D(sampler3D s, float3 t);

float4 tex3Dbias(sampler3D s, float4 t);

float4 tex3Dgrad(sampler3D s, float3 t, float3 ddx, float3 ddy);

float4 tex3Dlod(sampler3D s, float4 t);

float4 tex3Dproj(sampler3D s, float4 t);

float4 texCUBE(samplerCUBE s, float3 t);

float4 texCUBEbias(samplerCUBE s, float4 t);

float4 texCUBEgrad(samplerCUBE s, float3 t, float3 ddx, float3 ddy);

float4 texCUBElod(samplerCUBE s, float4 t);

float4 texCUBEproj(samplerCUBE s, float4 t);
