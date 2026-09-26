#ifndef EMINUS_FAR_DEPTH_GLSL
#define EMINUS_FAR_DEPTH_GLSL

#ifdef DEPTH_REVERSED
#define NEARER(a, b) ((a) > (b))
#define FARTHER(depth, bias) ((depth) - (bias))
#else
#define NEARER(a, b) ((a) < (b))
#define FARTHER(depth, bias) ((depth) + (bias))
#endif

#endif
