package com.yourname.streamci.streamci.service;
import com.yourname.streamci.streamci.model.Pipeline;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
public class PipelineService {

    public ArrayList<Pipeline> createFakePipelines(int n){

        ArrayList<Pipeline> fakePipelines = new ArrayList<>();
        String status = "on";

        for (int i = 0; i < n; i++) {
            if (i%2 == 0){
                status = "on";
            } else {
                status = "off";
            }
            fakePipelines.add(new Pipeline(i, "pipeline"+ i, status));
        }

        return fakePipelines;
    }
}
