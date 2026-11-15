package org.androidannotations.bench;

import java.util.Set;

import org.androidannotations.annotations.sharedpreferences.DefaultInt;
import org.androidannotations.annotations.sharedpreferences.DefaultStringSet;
import org.androidannotations.annotations.sharedpreferences.SharedPref;

@SharedPref(SharedPref.Scope.UNIQUE)
public interface BenchPrefsJava {

    @DefaultInt(3)
    int plain();

    @DefaultStringSet({ "a", "b" })
    Set<String> withValues();

    @DefaultStringSet({})
    Set<String> emptySet();
}
