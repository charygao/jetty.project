//
// ========================================================================
// Copyright (c) 1995-2020 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under
// the terms of the Eclipse Public License 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0
//
// This Source Code may also be made available under the following
// Secondary Licenses when the conditions for such availability set
// forth in the Eclipse Public License, v. 2.0 are satisfied:
// the Apache License v2.0 which is available at
// https://www.apache.org/licenses/LICENSE-2.0
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty.hazelcast.session;

import java.io.IOException;
import java.util.Arrays;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.XmlClientConfigBuilder;
import com.hazelcast.config.Config;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.SerializerConfig;
import com.hazelcast.config.XmlConfigBuilder;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.eclipse.jetty.server.session.AbstractSessionDataStoreFactory;
import org.eclipse.jetty.server.session.SessionData;
import org.eclipse.jetty.server.session.SessionDataStore;
import org.eclipse.jetty.server.session.SessionDataStoreFactory;
import org.eclipse.jetty.server.session.SessionHandler;

/**
 * Factory to construct {@link HazelcastSessionDataStore}
 */
public class HazelcastSessionDataStoreFactory
    extends AbstractSessionDataStoreFactory
    implements SessionDataStoreFactory
{

    private String hazelcastInstanceName = "JETTY_DISTRIBUTED_SESSION_INSTANCE";

    private boolean onlyClient;

    private String configurationLocation;

    private String mapName = "jetty-distributed-session-map";

    private HazelcastInstance hazelcastInstance;

    private MapConfig mapConfig;

    private boolean scavengeZombies = false;

    private String addresses;

    public boolean isScavengeZombies()
    {
        return scavengeZombies;
    }

    public void setScavengeZombies(boolean scavengeZombies)
    {
        this.scavengeZombies = scavengeZombies;
    }

    @Override
    public SessionDataStore getSessionDataStore(SessionHandler handler)
        throws Exception
    {
        HazelcastSessionDataStore hazelcastSessionDataStore = new HazelcastSessionDataStore();

        if (hazelcastInstance == null)
        {
            try
            {
                if (onlyClient)
                {
                    if (configurationLocation == null)
                    {
                        ClientConfig config = new ClientConfig();

                        if (addresses != null && !addresses.isEmpty())
                        {
                            config.getNetworkConfig().setAddresses(Arrays.asList(addresses.split(",")));
                        }

                        SerializerConfig sc = new SerializerConfig()
                            .setImplementation(new SessionDataSerializer())
                            .setTypeClass(SessionData.class);
                        config.getSerializationConfig().addSerializerConfig(sc);
                        hazelcastInstance = HazelcastClient.newHazelcastClient(config);
                    }
                    else
                    {
                        hazelcastInstance = HazelcastClient.newHazelcastClient(
                            new XmlClientConfigBuilder(configurationLocation).build());
                    }
                }
                else
                {
                    Config config;
                    if (configurationLocation == null)
                    {

                        SerializerConfig sc = new SerializerConfig()
                            .setImplementation(new SessionDataSerializer())
                            .setTypeClass(SessionData.class);
                        config = new Config();
                        config.getSerializationConfig().addSerializerConfig(sc);
                        // configure a default Map if null
                        if (mapConfig == null)
                        {
                            mapConfig = new MapConfig();
                            mapConfig.setName(mapName);
                        }
                        else
                        {
                            // otherwise we reuse the name
                            mapName = mapConfig.getName();
                        }
                        config.addMapConfig(mapConfig);
                    }
                    else
                    {
                        config = new XmlConfigBuilder(configurationLocation).build();
                    }
                    config.setInstanceName(hazelcastInstanceName);
                    hazelcastInstance = Hazelcast.getOrCreateHazelcastInstance(config);
                }
            }
            catch (IOException e)
            {
                throw new RuntimeException(e.getMessage(), e);
            }
        }
        // initialize the map
        hazelcastSessionDataStore.setSessionDataMap(hazelcastInstance.getMap(mapName));
        hazelcastSessionDataStore.setGracePeriodSec(getGracePeriodSec());
        hazelcastSessionDataStore.setSavePeriodSec(getSavePeriodSec());
        hazelcastSessionDataStore.setScavengeZombieSessions(scavengeZombies);
        return hazelcastSessionDataStore;
    }

    public boolean isOnlyClient()
    {
        return onlyClient;
    }

    /**
     * @param onlyClient if <code>true</code> the session manager will only connect to an external Hazelcast instance
     * and not use this JVM to start an Hazelcast instance
     */
    public void setOnlyClient(boolean onlyClient)
    {
        this.onlyClient = onlyClient;
    }

    public String getConfigurationLocation()
    {
        return configurationLocation;
    }

    public void setConfigurationLocation(String configurationLocation)
    {
        this.configurationLocation = configurationLocation;
    }

    public String getMapName()
    {
        return mapName;
    }

    public void setMapName(String mapName)
    {
        this.mapName = mapName;
    }

    public HazelcastInstance getHazelcastInstance()
    {
        return hazelcastInstance;
    }

    public void setHazelcastInstance(HazelcastInstance hazelcastInstance)
    {
        this.hazelcastInstance = hazelcastInstance;
    }

    public MapConfig getMapConfig()
    {
        return mapConfig;
    }

    public void setMapConfig(MapConfig mapConfig)
    {
        this.mapConfig = mapConfig;
    }

    public String getHazelcastInstanceName()
    {
        return hazelcastInstanceName;
    }

    public void setHazelcastInstanceName(String hazelcastInstanceName)
    {
        this.hazelcastInstanceName = hazelcastInstanceName;
    }

    public String getAddresses()
    {
        return addresses;
    }

    public void setAddresses(String addresses)
    {
        this.addresses = addresses;
    }
}
