package me.kkfish.interfaces;

import java.util.Map;

public interface CustomCompetitionData {

    String getTypeId();

    Map<String, Object> getRawData();

    void setData(String key, Object value);

    Object getData(String key);

    void reset();
}