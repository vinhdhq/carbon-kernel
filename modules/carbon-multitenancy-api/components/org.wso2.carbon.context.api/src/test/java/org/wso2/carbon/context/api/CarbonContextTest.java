/*
 *  Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.wso2.carbon.context.api;

import org.testng.Assert;
import org.testng.annotations.Test;
import org.wso2.carbon.context.api.internal.OSGiServiceHolder;
import org.wso2.carbon.kernel.Constants;
import org.wso2.carbon.multitenancy.DefaultTenantStore;
import org.wso2.carbon.multitenancy.TenantRuntime;
import org.wso2.carbon.multitenancy.api.Tenant;
import org.wso2.carbon.multitenancy.api.TenantStore;
import org.wso2.carbon.multitenancy.exception.TenantStoreException;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Test class for CarbonContext API usage.
 *
 * @since 5.0.0
 */
public class CarbonContextTest {
    private static final String TENANT_PROPERTY = "testProperty";
    private static final Path testDir = Paths.get("src", "test", "resources");

    CarbonContextTest() throws TenantStoreException {
        System.setProperty(Constants.CARBON_HOME, Paths.get(testDir.toString(), "carbon-home").toString());
        TenantStore tenantStore = new DefaultTenantStore();
        tenantStore.init();
        OSGiServiceHolder.getInstance().setTenantRuntime(new TenantRuntime(tenantStore));
    }

    @Test
    public void testCarbonContext() {
        CarbonContext carbonContext = PrivilegedCarbonContext.getCurrentContext();
        Assert.assertEquals(carbonContext.getServerTenant().getDomain(),
                org.wso2.carbon.context.api.Constants.DEFAULT_TENANT);
        Assert.assertEquals(carbonContext.getUserPrincipal(), null);
        Assert.assertEquals(carbonContext.getProperty("someProperty"), null);
    }

    @Test(dependsOnMethods = "testCarbonContext")
    public void testPrivilegeCarbonContext() throws TenantStoreException {
        Principal userPrincipal = () -> "test";
        String tenantPropertyValue = "testValue";
        Map<String, Object> properties = new HashMap<>();
        properties.put(TENANT_PROPERTY, tenantPropertyValue);
        String carbonContextPropertyKey = "KEY";
        Object carbonContextPropertyValue = "VALUE";
        PrivilegedCarbonContext privilegedCarbonContext =
                (PrivilegedCarbonContext) PrivilegedCarbonContext.getCurrentContext();
        Assert.assertEquals(PrivilegedCarbonContext.getCurrentContext().getServerTenant().getDomain(),
                org.wso2.carbon.context.api.Constants.DEFAULT_TENANT);

        try {
            privilegedCarbonContext.setUserPrincipal(userPrincipal);
            privilegedCarbonContext.setProperty(carbonContextPropertyKey, carbonContextPropertyValue);
            Tenant tenant = PrivilegedCarbonContext.getCurrentContext().getServerTenant();
            tenant.setProperties(properties);
            Assert.assertEquals(PrivilegedCarbonContext.getCurrentContext().getServerTenant().
                            getProperty(TENANT_PROPERTY), tenantPropertyValue);
            Assert.assertEquals(PrivilegedCarbonContext.getCurrentContext().getUserPrincipal(), userPrincipal);
            Assert.assertEquals(PrivilegedCarbonContext.getCurrentContext().getProperty(carbonContextPropertyKey),
                    carbonContextPropertyValue);
        } finally {
            PrivilegedCarbonContext.destroyCurrentContext();
        }

        Assert.assertEquals(PrivilegedCarbonContext.getCurrentContext().getUserPrincipal(), null);
    }

    @Test(dependsOnMethods = "testCarbonContext")
    public void testMultiThreadedCarbonContextInvocation() {
        IntStream.range(1, 10)
                .forEach(id ->
                        {
                            CarbonContextInvoker invoker = new CarbonContextInvoker("tenantDomain" + id,
                                    "tenantPropertyKey" + id, "tenantPropertyVal" + id, "ccPropertyKey" + id,
                                    "ccPropertyVal" + id);
                            invoker.start();
                        }
                );
    }

    class CarbonContextInvoker extends Thread {
        String tenantDomain;
        String tenantPropertyKey;
        String tenantPropertyValue;
        String carbonContextPropertyKey;
        Object carbonContextPropertyValue;

        CarbonContextInvoker(String tenantDomain, String tenantPropertyKey, String tenantPropertyValue,
                             String carbonContextPropertyKey, Object carbonContextPropertyValue) {
            this.tenantDomain = tenantDomain;
            this.tenantPropertyKey = tenantPropertyKey;
            this.tenantPropertyValue = tenantPropertyValue;
            this.carbonContextPropertyKey = carbonContextPropertyKey;
            this.carbonContextPropertyValue = carbonContextPropertyValue;
        }

        @Override
        public void run() {
            PrivilegedCarbonContext privilegedCarbonContext =
                    (PrivilegedCarbonContext) PrivilegedCarbonContext.getCurrentContext();
            Assert.assertEquals(PrivilegedCarbonContext.getCurrentContext().getServerTenant().getDomain(),
                    org.wso2.carbon.context.api.Constants.DEFAULT_TENANT);

            Principal userPrincipal = () -> "test";
            Map<String, Object> properties = new HashMap<>();
            properties.put(tenantPropertyKey, tenantPropertyValue);
            try {
                privilegedCarbonContext.setUserPrincipal(userPrincipal);
                privilegedCarbonContext.setProperty(carbonContextPropertyKey, carbonContextPropertyValue);

                CarbonContext carbonContext = PrivilegedCarbonContext.getCurrentContext();
                Tenant tenant = PrivilegedCarbonContext.getCurrentContext().getServerTenant();
                tenant.setProperties(properties);
                Assert.assertEquals(carbonContext.getServerTenant().getProperty(tenantPropertyKey),
                        tenantPropertyValue);
                Assert.assertEquals(carbonContext.getUserPrincipal(), userPrincipal);
                Assert.assertEquals(carbonContext.getProperty(carbonContextPropertyKey), carbonContextPropertyValue);
            } finally {
                PrivilegedCarbonContext.destroyCurrentContext();
            }
            Assert.assertEquals(PrivilegedCarbonContext.getCurrentContext().getUserPrincipal(), null);
        }
    }


    @Test(dependsOnMethods = "testMultiThreadedCarbonContextInvocation")
    public void testCarbonContextFaultyScenario2() {
        Principal userPrincipal1 = () -> "test1";
        Principal userPrincipal2 = () -> "test2";

        try {
            PrivilegedCarbonContext privilegedCarbonContext =
                    (PrivilegedCarbonContext) PrivilegedCarbonContext.getCurrentContext();
            Assert.assertEquals(PrivilegedCarbonContext.getCurrentContext().getServerTenant().getDomain(),
                    org.wso2.carbon.context.api.Constants.DEFAULT_TENANT);
            try {
                privilegedCarbonContext.setUserPrincipal(userPrincipal1);
                Assert.assertEquals(PrivilegedCarbonContext.getCurrentContext().getUserPrincipal(), userPrincipal1);
                privilegedCarbonContext.setUserPrincipal(userPrincipal2);
            } catch (Exception e) {
                Assert.assertTrue(e.getMessage().contains("Trying to override the already available user principal " +
                        "from " + userPrincipal1.toString() + " to " + userPrincipal2.toString()));
            }
        } finally {
            PrivilegedCarbonContext.destroyCurrentContext();
        }
    }

    @Test(dependsOnMethods = "testCarbonContextFaultyScenario2")
    public void testSystemTenantDomainCarbonContextPopulation1() throws TenantStoreException {
        String tenantDomain = "test-sys-domain";
        System.setProperty(org.wso2.carbon.context.api.Constants.TENANT_DOMAIN, tenantDomain);
        CarbonContext carbonContext = PrivilegedCarbonContext.getCurrentContext();
        Assert.assertEquals(carbonContext.getServerTenant().getDomain(), tenantDomain);
        Assert.assertEquals(PrivilegedCarbonContext.getCurrentContext().getServerTenant().getDomain(), tenantDomain);

        System.clearProperty(org.wso2.carbon.context.api.Constants.TENANT_DOMAIN);
    }
}