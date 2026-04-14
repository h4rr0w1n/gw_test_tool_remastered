package com.amhs.swim.test.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks manual validation marks (Pass/Fail) and notes for Cases and Messages 
 * as configured via the GUI Top Bar.
 */
public class CaseSessionState {
    public Boolean casePass = null; // true=pass, false=fail, null=unmarked
    public String caseNote = "";

    public Map<Integer, Boolean> msgPassMap = new HashMap<>();
    public Map<Integer, String> msgNoteMap = new HashMap<>();
    public Map<Integer, Boolean> msgLockedMap = new HashMap<>();

    public void setMsgPass(int msgIdx, Boolean pass) {
        msgPassMap.put(msgIdx, pass);
    }

    public void setMsgNote(int msgIdx, String note) {
        msgNoteMap.put(msgIdx, note);
    }

    public Boolean getMsgPass(int msgIdx) {
        return msgPassMap.get(msgIdx);
    }

    public String getMsgNote(int msgIdx) {
        return msgNoteMap.getOrDefault(msgIdx, "");
    }
}
