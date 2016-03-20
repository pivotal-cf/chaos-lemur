/*
 * Copyright 2014-2016 the original author or authors.
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

package io.pivotal.strepsirrhini.chaoslemur.infrastructure;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.MalformedURLException;

@Configuration
class InfrastructureConfiguration {

    @Bean
    @ConditionalOnProperty("aws.accessKeyId")
    AmazonEC2 amazonEC2(@Value("${aws.accessKeyId}") String accessKeyId,
                        @Value("${aws.secretAccessKey}") String secretAccessKey,
                        @Value("${aws.region:us-east-1}") String regionName) {

        AmazonEC2Client amazonEC2Client = new AmazonEC2Client(new BasicAWSCredentials(accessKeyId, secretAccessKey));
        Region region = Region.getRegion(Regions.fromName(regionName));
        amazonEC2Client.setEndpoint(region.getServiceEndpoint("ec2"));

        return amazonEC2Client;
    }

    @Bean
    @ConditionalOnBean(AmazonEC2.class)
    Infrastructure awsInfrastructure(StandardDirectorUtils directorUtils, AmazonEC2 amazonEC2) {
        return new AwsInfrastructure(directorUtils, amazonEC2);
    }

    @Bean
    @ConditionalOnProperty("vsphere.host")
    InventoryNavigatorFactory inventoryNavigatorFactory(@Value("${vsphere.host}") String host,
                                                        @Value("${vsphere.username}") String username,
                                                        @Value("${vsphere.password}") String password)
        throws MalformedURLException {

        return new StandardInventoryNavigatorFactory(host, username, password);
    }

    @Bean
    @ConditionalOnProperty("simple.infrastructure")
    Infrastructure simpleInfrastructure() {
        return new SimpleInfrastructure();
    }
    
    @Autowired
    DirectorUtils directorUtils;
    
    @Bean
    @ConditionalOnProperty("openstack.endpoint")
    Infrastructure jcloudsInfra(
    		@Value("${openstack.endpoint}") String endpoint,
            @Value("${openstack.tenant}") String tenant,    		
    		@Value("${openstack.username}") String username,
            @Value("${openstack.password}") String password,
            @Value("${openstack.proxyhost}") String proxyhost,
            @Value("${openstack.proxyport}") String proxyport
            ) {
    	
        return new JCloudsComputeInfrastructure(this.directorUtils,endpoint,tenant, username,password,proxyhost,proxyport);
    }
    


    @Bean
    @ConditionalOnBean(InventoryNavigatorFactory.class)
    Infrastructure vSphereInfrastructure(InventoryNavigatorFactory inventoryNavigatorFactory, StandardDirectorUtils directorUtils) {
        return new VSphereInfrastructure(directorUtils, inventoryNavigatorFactory);
    }

}
