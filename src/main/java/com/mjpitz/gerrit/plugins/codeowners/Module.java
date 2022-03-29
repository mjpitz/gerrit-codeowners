package com.mjpitz.gerrit.plugins.codeowners;

import com.google.gerrit.extensions.events.WorkInProgressStateChangedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.inject.AbstractModule;

public class Module extends AbstractModule {
    @Override
    protected void configure() {
        DynamicSet.bind(binder(), WorkInProgressStateChangedListener.class).to(ReviewAssigner.class);
    }
}
