package org.metaborg.spoofax.core.style;

import org.metaborg.spoofax.core.messages.ISourceRegion;

public interface IRegionStyle<T> {
    public abstract T fragment();

    public abstract ISourceRegion region();

    public abstract IStyle style();
}