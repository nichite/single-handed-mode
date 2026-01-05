package com.singlehandedmode;

import java.lang.reflect.Field;
import net.runelite.api.Model;

// DO NOT USE
public class HandRemover
{
    public static void amputate(Model model)
    {
        if (model == null) return;

        try
        {
            Field[] fields = model.getClass().getDeclaredFields();
            int vCount = model.getVerticesCount();

            for (Field f : fields)
            {
                f.setAccessible(true);

                // 1. Target "Medium" Geometry Arrays (The ones that worked)
                if (f.getType() == int[].class)
                {
                    int[] data = (int[]) f.get(model);
                    if (data == null) continue;

                    // Length Filter: Between 1x and 3x vertex count (Length 1392)
                    if (data.length >= vCount && data.length < (vCount * 3))
                    {
                        Stats stats = analyze(data);

                        // 2. STRICT SAFETY FILTER
                        // Height arrays are usually ~380. Width arrays are ~130.
                        // We set the cutoff at 250.
                        // If range > 250, it's Height (or Index Buffer). SKIP IT.
                        if (stats.range > 250)
                        {
                            continue;
                        }

                        // 3. PROCESS ONLY WIDTH
                        processWidth(data, stats);
                    }
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private static void processWidth(int[] data, Stats stats)
    {
        int center = (stats.min + stats.max) / 2;

        // ARM THRESHOLD:
        // Identify points that are sticking out > 30 units from the center.
        int rightThreshold = center + 30;
        int tuckPos = center + 20;

        for (int i = 0; i < data.length; i++)
        {
            // CUT THE "RIGHT" SIDE (High Values)
            if (data[i] > rightThreshold)
            {
                data[i] = tuckPos;
            }
        }
    }

    private static Stats analyze(int[] data)
    {
        if (data.length == 0) return new Stats(0,0,0);
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;

        // Sample first 100 for speed
        int limit = Math.min(data.length, 100);
        for(int i=0; i<limit; i++) {
            if(data[i] < min) min = data[i];
            if(data[i] > max) max = data[i];
        }
        return new Stats(min, max, max - min);
    }

    static class Stats { int min, max, range; Stats(int a, int b, int c){min=a;max=b;range=c;} }
}