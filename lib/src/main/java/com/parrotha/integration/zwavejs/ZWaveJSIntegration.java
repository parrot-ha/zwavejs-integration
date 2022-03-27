/**
 * Copyright (c) 2021 by the respective copyright holders.
 * All rights reserved.
 * <p>
 * This file is part of Parrot Home Automation Hub.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.parrotha.integration.zwavejs;

import com.parrotha.device.HubAction;
import com.parrotha.device.HubResponse;
import com.parrotha.integration.DeviceIntegration;
import com.parrotha.integration.extension.DeviceExcludeIntegrationExtension;
import com.parrotha.integration.extension.DeviceScanIntegrationExtension;
import com.parrotha.integration.extension.ResetIntegrationExtension;
import com.parrotha.internal.utils.HexUtils;
import com.parrotha.ui.PreferencesBuilder;
import com.parrotha.zwave.commands.networkmanagementinclusionv3.NodeAddStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

public class ZWaveJSIntegration extends DeviceIntegration implements DeviceExcludeIntegrationExtension, DeviceScanIntegrationExtension,
        ResetIntegrationExtension, ZWaveJSWSClient.EventListener {
    private static final Logger logger = LoggerFactory.getLogger(ZWaveJSIntegration.class);

    private ZWaveJSWSClient zWaveJSWSClient;

    @Override
    public void start() {

        boolean disabled = "true".equals(getSettingAsString("disabled"));
        if (disabled) {
            return;
        }

        String address = getSettingAsString("zwaveJSServerAddress", "localhost:3000");

        try {
            System.out.println("zwavejs connecting to " + address);
            zWaveJSWSClient = new ZWaveJSWSClient(new URI("ws://" + address));
            zWaveJSWSClient.connect();
            zWaveJSWSClient.addEventListener(this);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void stop() {
        if (zWaveJSWSClient != null) {
            zWaveJSWSClient.close();
        }
    }

    @Override
    public String getName() {
        return "Z-Wave via ZWave JS Server";
    }

    @Override
    public String getDescription() {
        ResourceBundle messages = ResourceBundle.getBundle("com.parrotha.zwavejs.MessageBundle");
        return messages.getString("integration.description");
    }

    @Override
    public Map<String, String> getDisplayInformation() {
        return null;
    }

    @Override
    public boolean removeIntegrationDevice(String deviceNetworkId) {
        //TODO: start exclusion process and trigger a request for user to exclude device
        return true;
    }

    @Override
    public HubResponse processAction(HubAction hubAction) {
        logger.debug("Got hubAction: " + hubAction.toString());

//        if (hubAction.getAction().startsWith("2001")) {
//            zWaveJSWSClient.setValue(HexUtils.hexStringToInt(hubAction.getDni()), 0x20, "targetValue",
//                    HexUtils.hexStringToInt(hubAction.getAction().substring("2001".length())));
//        }
        zWaveJSWSClient.send(hubAction.getAction());
        return null;
    }

    // the layout for the integration page
    @Override
    public List<Map<String, Object>> getPageLayout() {
        List<Map<String, Object>> sections = new ArrayList<>();
        List<Map<String, Object>> bodyList = new ArrayList<>();
        List<Map<String, Object>> columnList = new ArrayList<>();

        columnList.add(Map.of("name", "nodeId", "title", "Node ID", "data", "nodes.id"));
        bodyList.add(Map.of("name", "deviceTable", "title", "Devices", "type", "table", "columns", columnList));
        sections.add(Map.of("name", "deviceList", "title", "Device List", "body", bodyList));

        return sections;
    }

    @Override
    public Map<String, Object> getPageData() {
        Map<String, Object> pageData = new HashMap<>();

//        Map<Integer, ZWaveIPNode> nodeList = zipgwHandler.getzWaveIPNodeList();
        List<Map> nodes = new ArrayList<>();
//        for (Integer nodeId : nodeList.keySet()) {
//            nodes.add(Map.of("id", "0x" + HexUtils.integerToHexString(nodeId, 1)));
//        }
        pageData.put("nodes", nodes);
        pageData.put("excludeRunning", false);
        pageData.put("excludeStopped", true);
        return pageData;
    }

    private boolean excludeRunning = false;
    private boolean stoppingExclude = false;
    private List<Map<String, String>> excludedDevices;
    private String excludeMessage = null;

    @Override
    public boolean startExclude(Map options) {
        excludeMessage = null;
        excludedDevices = null;
        excludeRunning = zWaveJSWSClient.startExclusion();

        return excludeRunning;
    }

    @Override
    public boolean stopExclude(Map options) {
        stoppingExclude = true;
        if (zWaveJSWSClient.stopExclusion()) {
            //excludeRunning = false;
            return true;
        }
        return false;
    }

    @Override
    public Map getExcludeStatus(Map options) {
        Map<String, Object> excludeStatus = new HashMap<>();
        excludeStatus.put("running", excludeRunning);
        if (excludedDevices != null && excludedDevices.size() > 0) {
            excludeStatus.put("excludedDevices", excludedDevices);
        }
        return excludeStatus;
    }

    private boolean scanRunning = false;
    private List<Map<String, String>> addedDevices;
    private String addMessage = null;

    @Override
    public boolean startScan(Map options) {
        scanRunning = zWaveJSWSClient.startInclusion();
        return scanRunning;
    }

    public void processNodeAdd(NodeAddStatus nodeAddStatus) {
        scanRunning = false;
        if (nodeAddStatus.getStatus() == NodeAddStatus.ADD_NODE_STATUS_DONE) {
            addMessage = "Successfully added device";
            if (addedDevices == null) {
                addedDevices = new ArrayList<>();
            }
            addedDevices.add(Map.of("deviceNetworkId", HexUtils.integerToHexString(nodeAddStatus.getNewNodeId(), 1)));

            //TODO: process node and add to devices

            // get Node Info
            // get Manufacturer specifc report
            // set association group 1

        } else if (nodeAddStatus.getStatus() == NodeAddStatus.ADD_NODE_STATUS_FAILED ||
                nodeAddStatus.getStatus() == NodeAddStatus.ADD_NODE_STATUS_SECURITY_FAILED) {
            addMessage = "Failed to add device";
        }
    }

    @Override
    public boolean stopScan(Map options) {
        if (zWaveJSWSClient.stopInclusion()) {
            scanRunning = false;
            return true;
        }
        return false;
    }

    @Override
    public Map getScanStatus(Map options) {
        Map<String, Object> scanStatus = new HashMap<>();
        scanStatus.put("running", scanRunning);
        if (addedDevices != null && addedDevices.size() > 0) {
            scanStatus.put("foundDevices", addedDevices);
        }
        return scanStatus;
    }

    @Override
    public boolean reset(Map options) {
        stop();
//        return zipgwHandler.reset();
        return false;
    }

    @Override
    public String getResetWarning() {
        ResourceBundle messages = ResourceBundle.getBundle("com.parrotha.zwavejs.MessageBundle");
        return messages.getString("reset.warning");
    }

    @Override
    public Map<String, Object> getPreferencesLayout() {
        return new PreferencesBuilder()
                .withBoolInput("disabled",
                        "Disable",
                        "Disable ZWave JS Server Integration",
                        false,
                        true)
                .withTextInput("zwaveJSServerAddress",
                        "ZWave JS Server Address",
                        "ZWave JS Server Address",
                        false,
                        true)
                .build();
    }

    @Override
    public void settingValueChanged(List<String> keys) {
        if (logger.isDebugEnabled()) {
            logger.debug("values changed " + keys);
        }
        if (keys.contains("disabled")) {
            // restart the integration
            this.stop();
            this.start();
        }
    }

    @Override
    public void onEvent(EventMessage eventtMessage) {
        logger.debug("Got event " + eventtMessage.toString());
        if ("exclusion stopped".equals(eventtMessage.getEvent()) && "controller".equals(eventtMessage.getSource())) {
            // if stoppingExclude == true, then we stopped the exclude, otherwise some device was excluded
            if (!stoppingExclude) {
                excludeMessage = "Successfully excluded device";
                if (excludedDevices == null) {
                    excludedDevices = new ArrayList<>();
                }
                excludedDevices.add(Map.of("deviceNetworkId", "Unknown"));
            }
            stoppingExclude = false;
            excludeRunning = false;
        }
    }
}
