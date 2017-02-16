/*
 * Copyright 2017 Danish Maritime Authority.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.niord.core.promulgation;

import org.niord.core.message.vo.SystemMessageVo;
import org.niord.core.promulgation.vo.RadioPromulgationVo;

import javax.annotation.PostConstruct;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.ejb.Startup;

/**
 * Manages radio promulgations.
 *
 * Radio promulgations are verbose textual versions of the messages suitable for being read up on the radio
 * and sent to the radio station via e-mail.
 */
@Singleton
@Startup
@Lock(LockType.READ)
@SuppressWarnings("unused")
public class RadioPromulgationService extends BasePromulgationService {

    public static final int PRIORITY = 10;


    /**
     * Registers the promulgation service with the promulgation manager
     */
    @PostConstruct
    public void init() {
        registerPromulgationService();
    }


    /** {@inheritDoc} */
    @Override
    public String getType() {
        return RadioPromulgation.TYPE;
    }


    /** {@inheritDoc} */
    @Override
    public int getDefaultPriority() {
        return PRIORITY;
    }


    /** {@inheritDoc} */
    @Override
    public void onLoadSystemMessage(SystemMessageVo message) throws PromulgationException {
        RadioPromulgationVo radio = message.promulgation(RadioPromulgationVo.class, getType());
        if (radio == null) {
            radio = new RadioPromulgationVo();
            message.getPromulgations().add(radio);
        }
    }
}
