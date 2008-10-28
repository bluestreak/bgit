package com.atlassian.labs.bamboo.git;

import java.util.*;

import com.atlassian.bamboo.reports.collector.*;
import org.jfree.data.general.Dataset;

/**
 * A basic report.
 */
public class ExampleReport implements ReportCollector
{


    public Dataset getDataset()
    {
        return null;
    }

    public void setResultsList(List list)
    {
    }

    public void setParams(Map map)
    {
    }

    public String getPeriodRange()
    {
        return null;
    }
    
    
}