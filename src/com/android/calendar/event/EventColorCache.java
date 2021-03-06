/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.calendar.event;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * 달력계정 이름과 형식에 해당한 Event Color와 Key값들을을 보관하기 위해 리용되는 클라스
 */
public class EventColorCache implements Serializable {

    private static final long serialVersionUID = 2L;
    private static final String SEPARATOR = "::";

    private final Map<String, ArrayList<Integer>> mColorPaletteMap;
    private final Map<String, String> mColorKeyMap;

    public EventColorCache() {
        mColorPaletteMap = new HashMap<>();
        mColorKeyMap = new HashMap<>();
    }

    /**
     * Cache에 색을 보관
     * @param accountName 달력계정이름
     * @param accountType 달력계정형식
     * @param displayColor 색
     * @param colorKey Key
     */
    public void insertColor(String accountName, String accountType, int displayColor,
            String colorKey) {
        mColorKeyMap.put(createKey(accountName, accountType, displayColor), colorKey);
        String key = createKey(accountName, accountType);
        ArrayList<Integer> colorPalette;
        if ((colorPalette = mColorPaletteMap.get(key)) == null) {
            colorPalette = new ArrayList<Integer>();
        }
        colorPalette.add(displayColor);
        mColorPaletteMap.put(key, colorPalette);
    }

    /**
     * 달력계정에 해당한 색갈목록을 돌려준다.
     * @param accountName 계정이름
     * @param accountType 계정형태
     */
    public int[] getColorArray(String accountName, String accountType) {
        ArrayList<Integer> colors = mColorPaletteMap.get(createKey(accountName, accountType));
        if (colors == null) {
            return null;
        }
        int[] ret = new int[colors.size()];
        for (int i = 0; i < ret.length; i++) {
            ret[i] = colors.get(i);
        }
        return ret;
    }

    /**
     * 색갈에 해당한 key를 돌려준다
     * @param accountName 계정이름
     * @param accountType 계정형태
     * @param displayColor 색
     */
    public String getColorKey(String accountName, String accountType, int displayColor) {
        return mColorKeyMap.get(createKey(accountName, accountType, displayColor));
    }

    /**
     * 색갈들을 정렬하여 다시 보관한다.
     * @param comparator 비교연산객체 {@link Comparator }
     */
    public void sortPalettes(Comparator<Integer> comparator) {
        for (String key : mColorPaletteMap.keySet()) {
            ArrayList<Integer> palette = mColorPaletteMap.get(key);
            Integer[] sortedColors = new Integer[palette.size()];
            Arrays.sort(palette.toArray(sortedColors), comparator);
            palette.clear();
            Collections.addAll(palette, sortedColors);
            mColorPaletteMap.put(key, palette);
        }
    }

    /**
     * 달력계정에 해당한 key를 돌려준다
     * @param accountName   계정이름
     * @param accountType   계정형태
     */
    private String createKey(String accountName, String accountType) {
        return accountName +
                SEPARATOR +
                accountType;
    }

    /**
     * 달력계정의 색에 해당한 key를 돌려준다.
     * @param accountName 계정이름
     * @param accountType 계정형태
     * @param displayColor 색
     */
    private String createKey(String accountName, String accountType, int displayColor) {
        return createKey(accountName, accountType) +
                SEPARATOR +
                displayColor;
    }
}
