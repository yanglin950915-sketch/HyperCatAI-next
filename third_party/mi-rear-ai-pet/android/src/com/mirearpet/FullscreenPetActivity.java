package com.mirearpet;

/**
 * Dedicated front-screen fullscreen AI Pet activity for regular phones / tablets.
 * Kept separate from RearPetActivity so rear-display projection and front-screen mode
 * never share a task or window configuration.
 */
public class FullscreenPetActivity extends RearPetActivity {
    @Override
    protected boolean isFrontFullscreenMode() {
        return true;
    }
}
