#ifndef EMINUS_FAR_ALPHA_GLSL
#define EMINUS_FAR_ALPHA_GLSL

bool eminus_alpha_kept(float alpha, int compare, float reference) {
    if (compare == ALPHA_LESS) {
        return alpha < reference;
    }
    if (compare == ALPHA_EQUAL) {
        return alpha == reference;
    }
    if (compare == ALPHA_LEQUAL) {
        return alpha <= reference;
    }
    if (compare == ALPHA_GREATER) {
        return alpha > reference;
    }
    if (compare == ALPHA_NOTEQUAL) {
        return alpha != reference;
    }
    if (compare == ALPHA_GEQUAL) {
        return alpha >= reference;
    }
    return false;
}

#endif
