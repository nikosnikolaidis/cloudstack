// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package org.apache.cloudstack.api.command.user.autoscale;

import java.util.List;

import org.apache.cloudstack.api.*;
import org.apache.log4j.Logger;

import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.response.AutoScaleVmGroupResponse;
import org.apache.cloudstack.api.response.AutoScalePolicyResponse;
import org.apache.cloudstack.api.response.AutoScaleVmProfileResponse;
import org.apache.cloudstack.api.response.FirewallRuleResponse;
import com.cloud.async.AsyncJob;
import com.cloud.event.EventTypes;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.network.as.AutoScaleVmGroup;
import com.cloud.network.rules.LoadBalancer;

@APICommand(name = "createAutoScaleVmGroup", description = "Creates and automatically starts a virtual machine based on a service offering, disk offering, and template.", responseObject = AutoScaleVmGroupResponse.class)
public class CreateAutoScaleVmGroupCmd extends BaseAsyncCreateCmd {
    public static final Logger s_logger = Logger.getLogger(CreateAutoScaleVmGroupCmd.class.getName());

    private static final String s_name = "autoscalevmgroupresponse";

    // ///////////////////////////////////////////////////
    // ////////////// API parameters /////////////////////
    // ///////////////////////////////////////////////////

    @Parameter(name = ApiConstants.LBID, type = CommandType.UUID, entityType = FirewallRuleResponse.class,
            required = true, description = "the ID of the load balancer rule")
    private long lbRuleId;

    @Parameter(name = ApiConstants.MIN_MEMBERS, type = CommandType.INTEGER, required = true, description = "the minimum number of members in the vmgroup, the number of instances in the vm group will be equal to or more than this number.")
    private int minMembers;

    @Parameter(name = ApiConstants.MAX_MEMBERS, type = CommandType.INTEGER, required = true, description = "the maximum number of members in the vmgroup, The number of instances in the vm group will be equal to or less than this number.")
    private int maxMembers;

    @Parameter(name = ApiConstants.INTERVAL, type = CommandType.INTEGER, description = "the frequency at which the conditions have to be evaluated")
    private Integer interval;

    @Parameter(name = ApiConstants.SCALEUP_POLICY_IDS, type = CommandType.LIST, collectionType = CommandType.UUID, entityType = AutoScalePolicyResponse.class,
            required = true, description = "list of scaleup autoscale policies")
    private List<Long> scaleUpPolicyIds;

    @Parameter(name = ApiConstants.SCALEDOWN_POLICY_IDS, type = CommandType.LIST, collectionType = CommandType.UUID, entityType = AutoScalePolicyResponse.class,
            required = true, description = "list of scaledown autoscale policies")
    private List<Long> scaleDownPolicyIds;

    @Parameter(name = ApiConstants.VMPROFILE_ID, type = CommandType.UUID, entityType = AutoScaleVmProfileResponse.class,
            required = true, description = "the autoscale profile that contains information about the vms in the vm group.")
    private long profileId;

    // ///////////////////////////////////////////////////
    // ///////////////// Accessors ///////////////////////
    // ///////////////////////////////////////////////////


    public int getMinMembers() {
        return minMembers;
    }

    public int getMaxMembers() {
        return maxMembers;
    }

    public Integer getInterval() {
        return interval;
    }

    public long getProfileId() {
        return profileId;
    }

    public List<Long> getScaleUpPolicyIds() {
        return scaleUpPolicyIds;
    }

    public List<Long> getScaleDownPolicyIds() {
        return scaleDownPolicyIds;
    }

    public long getLbRuleId() {
        return lbRuleId;
    }

    // ///////////////////////////////////////////////////
    // ///////////// API Implementation///////////////////
    // ///////////////////////////////////////////////////

    @Override
    public String getCommandName() {
        return s_name;
    }

    public static String getResultObjectName() {
        return "autoscalevmgroup";
    }

    @Override
    public long getEntityOwnerId() {
        LoadBalancer lb = _entityMgr.findById(LoadBalancer.class, getLbRuleId());
        if (lb == null) {
            throw new InvalidParameterValueException("Unable to find loadbalancer by lbRuleId");
        }
        return lb.getAccountId();
    }

    public void setLbRuleId(Long lbRuleId) {
        this.lbRuleId = lbRuleId;
    }

    @Override
    public String getEventType() {
        return EventTypes.EVENT_AUTOSCALEVMGROUP_CREATE;
    }

    @Override
    public String getCreateEventType() {
        return EventTypes.EVENT_AUTOSCALEVMGROUP_CREATE;
    }

    @Override
    public String getCreateEventDescription() {
        return "creating AutoScale Vm Group";
    }

    @Override
    public String getEventDescription() {
        return "configuring AutoScale Vm Group. Vm Group Id: " + getEntityId();
    }

    @Override
    public AsyncJob.Type getInstanceType() {
        return AsyncJob.Type.AutoScaleVmGroup;
    }

    @Override
    public void create() throws ResourceAllocationException {
        AutoScaleVmGroup result = _autoScaleService.createAutoScaleVmGroup(this);
        if (result != null) {
            this.setEntityId(result.getId());
            this.setEntityUuid(result.getUuid());
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to create Autoscale Vm Group");
        }
    }

    @Override
    public void execute() {
        boolean success = false;
        AutoScaleVmGroup vmGroup = null;
        try
        {
            success = _autoScaleService.configureAutoScaleVmGroup(this);
            if (success) {
                vmGroup = _entityMgr.findById(AutoScaleVmGroup.class, getEntityId());
                AutoScaleVmGroupResponse responseObject = _responseGenerator.createAutoScaleVmGroupResponse(vmGroup);
                setResponseObject(responseObject);
                responseObject.setResponseName(getCommandName());
            }
        } catch (Exception ex) {
            // TODO what will happen if Resource Layer fails in a step inbetween
            s_logger.warn("Failed to create autoscale vm group", ex);
        } finally {
            if (!success || vmGroup == null) {
                _autoScaleService.deleteAutoScaleVmGroup(getEntityId());
                throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to create Autoscale Vm Group");
            }
        }
    }
}
