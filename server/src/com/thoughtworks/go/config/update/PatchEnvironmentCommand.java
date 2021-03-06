/*
 * Copyright 2016 ThoughtWorks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.thoughtworks.go.config.update;

import com.thoughtworks.go.config.*;
import com.thoughtworks.go.config.commands.EntityConfigUpdateCommand;
import com.thoughtworks.go.config.merge.MergeEnvironmentConfig;
import com.thoughtworks.go.i18n.Localizable;
import com.thoughtworks.go.i18n.LocalizedMessage;
import com.thoughtworks.go.server.domain.Username;
import com.thoughtworks.go.server.service.GoConfigService;
import com.thoughtworks.go.server.service.result.HttpLocalizedOperationResult;
import com.thoughtworks.go.server.service.result.LocalizedOperationResult;
import com.thoughtworks.go.serverhealth.HealthStateType;

import java.util.ArrayList;
import java.util.List;

public class PatchEnvironmentCommand extends EnvironmentCommand implements EntityConfigUpdateCommand<EnvironmentConfig> {
    private final GoConfigService goConfigService;
    private final EnvironmentConfig environmentConfig;
    private final List<String> pipelinesToAdd;
    private final List<String> pipelinesToRemove;
    private final List<String> agentsToAdd;
    private final List<String> agentsToRemove;
    private final Username username;
    private final Localizable.CurryableLocalizable actionFailed;
    private final HttpLocalizedOperationResult result;

    public PatchEnvironmentCommand(GoConfigService goConfigService, EnvironmentConfig environmentConfig, List<String> pipelinesToAdd, List<String> pipelinesToRemove, List<String> agentsToAdd, List<String> agentsToRemove, Username username, Localizable.CurryableLocalizable actionFailed, HttpLocalizedOperationResult result) {
        super(actionFailed, environmentConfig, result);

        this.goConfigService = goConfigService;
        this.environmentConfig = environmentConfig;
        this.pipelinesToAdd = pipelinesToAdd;
        this.pipelinesToRemove = pipelinesToRemove;
        this.agentsToAdd = agentsToAdd;
        this.agentsToRemove = agentsToRemove;
        this.username = username;
        this.actionFailed = actionFailed;
        this.result = result;
    }

    @Override
    public void update(CruiseConfig configForEdit) throws Exception {
        EnvironmentConfig environmentConfig = configForEdit.getEnvironments().named(this.environmentConfig.name());

        for (String uuid : agentsToAdd) {
            environmentConfig.addAgent(uuid);
        }

        for (String uuid : agentsToRemove) {
            environmentConfig.removeAgent(uuid);
        }

        for (String pipelineName : pipelinesToAdd) {
            environmentConfig.addPipeline(new CaseInsensitiveString(pipelineName));
        }

        for (String pipelineName : pipelinesToRemove) {
            environmentConfig.removePipeline(new CaseInsensitiveString(pipelineName));
        }
    }

    @Override
    public boolean isValid(CruiseConfig preprocessedConfig) {
        boolean isValid = validateRemovePipelines(preprocessedConfig);
        isValid = isValid && validateRemoveAgents(preprocessedConfig);
        return isValid && super.isValid(preprocessedConfig);
    }

    private boolean validateRemoveAgents(CruiseConfig preprocessedConfig) {
        EnvironmentConfig preprocessedEnvironmentConfig = preprocessedConfig.getEnvironments().find(environmentConfig.name());
        if(preprocessedEnvironmentConfig instanceof MergeEnvironmentConfig){
            for (String agentToRemove : agentsToRemove) {
                if(preprocessedEnvironmentConfig.containsAgentRemotely(agentToRemove)){
                    String origin = ((MergeEnvironmentConfig) preprocessedEnvironmentConfig).getOriginForAgent(agentToRemove).displayName();
                    String message = String.format("Agent with uuid '%s' cannot be removed from environment '%s' as the association has been defined remotely in [%s]",
                            agentToRemove, environmentConfig.name(), origin);
                    result.badRequest(actionFailed.addParam(message));
                    return false;
                }
            }
        }

        EnvironmentConfig environmentConfig = this.environmentConfig;
        for (String agentToRemove : agentsToRemove) {
            if(!environmentConfig.hasAgent(agentToRemove)){
                String message = String.format("Agent with uuid '%s' does not exist in environment '%s'", agentToRemove, environmentConfig.name());
                result.badRequest(actionFailed.addParam(message));
                return false;
            }
        }

        return true;
    }

    private boolean validateRemovePipelines(CruiseConfig preprocessedConfig) {
        EnvironmentConfig preprocessedEnvironmentConfig = preprocessedConfig.getEnvironments().find(environmentConfig.name());
        if(preprocessedEnvironmentConfig instanceof MergeEnvironmentConfig){
            for (String pipelineToRemove : pipelinesToRemove) {
                if(preprocessedEnvironmentConfig.containsPipelineRemotely(new CaseInsensitiveString(pipelineToRemove))){
                    String origin = ((MergeEnvironmentConfig) preprocessedEnvironmentConfig).getOriginForPipeline(new CaseInsensitiveString(pipelineToRemove)).displayName();
                    String message = String.format("Pipeline '%s' cannot be removed from environment '%s' as the association has been defined remotely in [%s]",
                            pipelineToRemove, environmentConfig.name(), origin);
                    result.badRequest(actionFailed.addParam(message));
                    return false;
                }
            }
        }

        EnvironmentConfig environmentConfig = this.environmentConfig;
        for (String pipelineToRemove : pipelinesToRemove) {
            if(!environmentConfig.containsPipeline(new CaseInsensitiveString(pipelineToRemove))){
                String message = String.format("Pipeline '%s' does not exist in environment '%s'", pipelineToRemove, environmentConfig.name());
                result.badRequest(actionFailed.addParam(message));
                return false;
            }
        }

        return true;
    }

    @Override
    public void clearErrors() {
        BasicCruiseConfig.clearErrors(environmentConfig);
    }

    @Override
    public boolean canContinue(CruiseConfig cruiseConfig) {
        if (!goConfigService.isAdministrator(username.getUsername())) {
            Localizable noPermission = LocalizedMessage.string("NO_PERMISSION_TO_UPDATE_ENVIRONMENT", environmentConfig.name().toString(), username.getDisplayName());
            result.unauthorized(noPermission, HealthStateType.unauthorised());
            return false;
        }
        return true;
    }
}
