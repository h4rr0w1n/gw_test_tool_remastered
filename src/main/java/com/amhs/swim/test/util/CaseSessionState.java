package com.amhs.swim.test.util;

/**
 * Tracks manual validation marks (Pass/Fail) and notes for Cases 
 * as configured via the GUI Top Bar.
 */
public class CaseSessionState {
    public Boolean casePass = null; // true=pass, false=fail, null=unmarked
    public String caseNote = "";
}
