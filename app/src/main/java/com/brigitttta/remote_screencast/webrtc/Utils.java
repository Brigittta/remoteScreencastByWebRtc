package com.brigitttta.remote_screencast.webrtc;


import com.blankj.utilcode.util.ArrayUtils;

import org.webrtc.SessionDescription;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;

public class Utils {
    static List<MediaCodecs.VideoCodec> videoCodecs = ArrayUtils.asArrayList(MediaCodecs.VideoCodec.H265);
    static List<MediaCodecs.AudioCodec> audioCodecs = ArrayUtils.asArrayList(MediaCodecs.AudioCodec.AAC);

    static public SessionDescription preferCodecs(SessionDescription sdp, boolean video) {

        LinkedHashSet<String> preferredCodecs = new LinkedHashSet<>();
        if (video) {
            for (MediaCodecs.VideoCodec codec : videoCodecs) {
                preferredCodecs.add(codec.name);
            }
            preferredCodecs.add("red");
            preferredCodecs.add("ulpfec");
        } else {
            for (MediaCodecs.AudioCodec codec : audioCodecs) {
                preferredCodecs.add(codec.name);
            }
            preferredCodecs.add("CN");
            preferredCodecs.add("telephone-event");
        }

        return preferCodec(sdp, preferredCodecs, video);
    }

    static public SessionDescription preferCodec(SessionDescription originalSdp,
                                                 LinkedHashSet<String> preferredCodecs, boolean video) {
        String[] lines = originalSdp.description.split("(\r\n|\n)");
        ArrayList<String> newLines = new ArrayList<>();

        int audioMLineIndex = -1;
        int videoMLineIndex = -1;
        //<codecName, payloadType>
        HashMap<String, ArrayList<String>> preferredPayloadTypes = new HashMap<>();
        //skipped all video payload types when dealing with audio codecs, and vice versa.
        HashSet<String> misMatchedPayloadTypes = new HashSet<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.startsWith("a=rtpmap:")) {
                String payloadType = line.split(" ")[0].split(":")[1];
                String codecName = line.split(" ")[1].split("/")[0];
                boolean typeMismatched = video ? MediaCodecs.VideoCodec.get(codecName) == MediaCodecs.VideoCodec.INVALID
                        : MediaCodecs.AudioCodec.get(codecName) == MediaCodecs.AudioCodec.INVALID;
                boolean codecPreferred = preferredCodecs.contains(codecName);
                boolean rtxPreferred = codecName.equals("rtx")
                        && containsValue(preferredPayloadTypes, lines[i + 1].split("apt=")[1]);
                if (codecPreferred || rtxPreferred) {
                    putEntry(preferredPayloadTypes, codecName, payloadType);
                } else if (typeMismatched && !codecName.equals("rtx")) {
                    misMatchedPayloadTypes.add(payloadType);
                } else {
                    continue;
                }
            } else if (line.startsWith("a=rtcp-fb:") || line.startsWith("a=fmtp:")) {
                String payloadType = line.split(" ")[0].split(":")[1];
                if (!misMatchedPayloadTypes.contains(payloadType)
                        && !containsValue(preferredPayloadTypes, payloadType)) {
                    continue;
                }
            } else if (line.startsWith("m=audio")) {
                audioMLineIndex = newLines.size();
            } else if (line.startsWith("m=video")) {
                videoMLineIndex = newLines.size();
            }
            newLines.add(line);
        }

        if (!video && audioMLineIndex != -1) {
            newLines.set(audioMLineIndex, changeMLine(newLines.get(audioMLineIndex),
                    preferredCodecs,
                    preferredPayloadTypes));
        }
        if (video && videoMLineIndex != -1) {
            newLines.set(videoMLineIndex, changeMLine(newLines.get(videoMLineIndex),
                    preferredCodecs,
                    preferredPayloadTypes));
        }
        String newSdp = joinString(newLines, "\r\n", true);

        return new SessionDescription(originalSdp.type, newSdp);
    }

    private static boolean containsValue(HashMap<String, ArrayList<String>> payloadTypes, String value) {
        for (ArrayList<String> v : payloadTypes.values()) {
            for (String s : v) {
                if (s.equals(value)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String changeMLine(String mLine, LinkedHashSet<String> preferredCodecs,
                                      HashMap<String, ArrayList<String>> preferredPayloadTypes) {
        List<String> oldMLineParts = Arrays.asList(mLine.split(" "));
        List<String> mLineHeader = oldMLineParts.subList(0, 3);

        ArrayList<String> newMLineParts = new ArrayList<>(mLineHeader);
        for (String preferredCodec : preferredCodecs) {
            if (preferredPayloadTypes.containsKey(preferredCodec)) {
                newMLineParts.addAll(preferredPayloadTypes.get(preferredCodec));
            }
        }
        if (preferredPayloadTypes.containsKey("rtx")) {
            newMLineParts.addAll(preferredPayloadTypes.get("rtx"));
        }
        return joinString(newMLineParts, " ", false);
    }

    private static String joinString(
            ArrayList<String> strings, String delimiter, boolean delimiterAtEnd) {
        Iterator<String> iter = strings.iterator();
        if (!iter.hasNext()) {
            return "";
        }
        StringBuilder buffer = new StringBuilder(iter.next());
        while (iter.hasNext()) {
            buffer.append(delimiter).append(iter.next());
        }
        if (delimiterAtEnd) {
            buffer.append(delimiter);
        }
        return buffer.toString();
    }

    private static void putEntry(HashMap<String, ArrayList<String>> payloadTypes, String key,
                                 String value) {
        if (payloadTypes.containsKey(key)) {
            payloadTypes.get(key).add(value);
        } else {
            ArrayList<String> valueList = new ArrayList<>();
            valueList.add(value);
            payloadTypes.put(key, valueList);
        }
    }
}
