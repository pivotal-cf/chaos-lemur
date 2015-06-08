/*
 * Copyright 2014-2015 the original author or authors.
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

package io.pivotal.xd.chaoslemur;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Random;

@Component
final class RandomFateEngine implements FateEngine {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final String[] blacklist;
    
    private final String[] whitelist;

    private final String defaultProbability;

    private final Environment environment;

    private final Random random;

    @Autowired
    RandomFateEngine(@Value("${blacklist:}") String[] blacklist, @Value("${whitelist:}") String[] whitelist,
            @Value("${default.probability:0.2}") Float defaultProbability, Environment environment, Random random) {
        this.blacklist = blacklist;
        this.whitelist = whitelist;
        this.defaultProbability = defaultProbability.toString();
        this.environment = environment;
        this.random = random;

        this.logger.info("Blacklist: {}", StringUtils.arrayToCommaDelimitedString(blacklist));
        this.logger.info("Whitelist: {}", StringUtils.arrayToCommaDelimitedString(whitelist));
        this.logger.info("Default probability: {}", defaultProbability);
    }

    @Override
    public Boolean shouldDie(Member member) {
        if (!isWhitelisted(member) || isBlacklisted(member)) {
            return false;
        }

        Float probability = new Precedence<String>()
                .candidate(() -> getProbability(member.getJob()))
                .candidate(() -> getProbability(member.getDeployment()))
                .candidate(this.defaultProbability)
                .get(Float::parseFloat);

        return this.random.nextFloat() < probability;
    }

    private boolean isWhitelisted(Member member) {
        if(this.whitelist.length == 0){
            return true;
        }
        return Arrays.stream(this.whitelist)
                .anyMatch(s -> member.getDeployment().equalsIgnoreCase(s) || member.getJob().equalsIgnoreCase(s));
    }

    private boolean isBlacklisted(Member member) {
        return Arrays.stream(this.blacklist)
                .anyMatch(s -> member.getDeployment().equalsIgnoreCase(s) || member.getJob().equalsIgnoreCase(s));
    }

    private String getProbability(String name) {
        return this.environment.getProperty(getProbabilityKey(name));
    }

    private String getProbabilityKey(String name) {
        return String.format("%s.probability", name);
    }
}
