/*
 * Copyright 2020-present Open Networking Foundation
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
package nycu.winlab.groupmeter;

import org.onosproject.core.ApplicationId;
import org.onosproject.net.config.Config;

public class NameConfig extends Config<ApplicationId> {
    public static final String HOST1 = "host-1";
    public static final String HOST2 = "host-2";
    public static final String MAC1 = "mac-1";
    public static final String MAC2 = "mac-2";
    public static final String IP1 = "ip-1";
    public static final String IP2 = "ip-2";
    public String getHost1() {
        return get(HOST1, null);
    }
    public String getHost2() {
        return get(HOST2, null);
    }
    public String getMac1() {
        return get(MAC1, null);
    }
    public String getMac2() {
        return get(MAC2, null);
    }
    public String getIp1() {
        return get(IP1, null);
    }
    public String getIp2() {
        return get(IP2, null);
    }
    @Override
    public boolean isValid() {
        return getHost1() != null && getHost2() != null;
    }
}
