package com.singlehandedmode;

import java.lang.reflect.Field;
import net.runelite.api.Model;

public class PsychadelicTrip {
    public static void amputate(Model model)
    {
        if (model == null) return;

        try
        {
            Field[] fields = model.getClass().getDeclaredFields();

            // Loop through EVERY field in the class
            for (Field f : fields)
            {
                f.setAccessible(true);
                Class<?> type = f.getType();

                // 1. NUKE INTEGERS
                if (type == int[].class)
                {
                    int[] data = (int[]) f.get(model);
                    // Only target the Shared Buffer size (6500)
                    if (data != null && data.length >= 6000)
                    {
                        // Add 50 to EVERYTHING.
                        // If this is Geometry, you will explode.
                        // If this is Indices, you will spaghettify.
                        for (int i = 0; i < data.length; i++) {
                            data[i] += 50;
                        }
                    }
                }
                // 2. NUKE FLOATS
                else if (type == float[].class)
                {
                    float[] data = (float[]) f.get(model);
                    if (data != null && data.length >= 6000)
                    {
                        for (int i = 0; i < data.length; i++) {
                            data[i] += 50.0f;
                        }
                    }
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}
