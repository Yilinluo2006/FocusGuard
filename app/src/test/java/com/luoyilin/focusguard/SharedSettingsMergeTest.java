package com.luoyilin.focusguard;
import org.junit.Test;
import org.json.*;
import static org.junit.Assert.*;
public class SharedSettingsMergeTest {
    private String value(String tasks,int time)throws Exception{return SharedSettingsData.canonical(new JSONObject().put("tasks",new JSONArray(tasks)).put("limits",new JSONArray()).put("sleep",new JSONObject().put("minute",time)));}
    @Test public void differentTaskEditsMergeAndDeletionsStayDeleted()throws Exception{
        String base=value("[{\"id\":\"a\",\"name\":\"original\"},{\"id\":\"b\",\"name\":\"remove\"}]",100);
        String local=value("[{\"id\":\"a\",\"name\":\"local edit\"}]",100);
        String remote=value("[{\"id\":\"a\",\"name\":\"original\"},{\"id\":\"b\",\"name\":\"remove\"},{\"id\":\"c\",\"name\":\"remote add\"}]",200);
        JSONObject merged=new JSONObject(SharedSettingsData.merge(base,local,remote));
        assertEquals(2,merged.getJSONArray("tasks").length());assertEquals("local edit",merged.getJSONArray("tasks").getJSONObject(0).getString("name"));assertEquals(200,merged.getJSONObject("sleep").getInt("minute"));
    }
    @Test public void sameTaskEditedDifferentlyRequiresChoice()throws Exception{
        try{SharedSettingsData.merge(value("[{\"id\":\"a\",\"name\":\"base\"}]",100),value("[{\"id\":\"a\",\"name\":\"local\"}]",100),value("[{\"id\":\"a\",\"name\":\"remote\"}]",100));fail();}catch(IllegalStateException expected){}
    }
    @Test public void twoSleepChangesCannotSilentlyMix()throws Exception{
        try{SharedSettingsData.merge(value("[]",100),value("[]",200),value("[]",300));fail();}catch(IllegalStateException expected){}
    }
}
