package com.trustylistener.domain.model

/**
 * Classification mode that controls preprocessing and ensemble behavior.
 * 
 * - BALANCED: Default mode with full preprocessing and ensemble (best for general use)
 * - SENSITIVE: Reduced preprocessing, better for detecting specific sounds (singing, cough, etc.)
 * - RAW: Minimal preprocessing, closest to original YAMNet behavior
 */
enum class ClassificationMode {
    /**
     * Default balanced mode:
     * - Full preprocessing (high-pass, pre-emphasis, noise gate)
     * - 3-window ensemble with weighted fusion
     * - EMA smoothing (α=0.6)
     */
    BALANCED,
    
    /**
     * Sensitive mode for detecting specific sounds:
     * - Reduced noise gate (gentler gating)
     * - 2-window ensemble
     * - Faster EMA (α=0.75)
     */
    SENSITIVE,
    
    /**
     * Raw mode - minimal processing:
     * - Only DC removal and normalization (no advanced preprocessing)
     * - No ensemble (single window)
     * - No temporal smoothing
     */
    RAW
}
