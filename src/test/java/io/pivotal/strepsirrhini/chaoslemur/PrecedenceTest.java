/*
 * Copyright 2014-2017 the original author or authors.
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

package io.pivotal.strepsirrhini.chaoslemur;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class PrecedenceTest {

    @Test(expected = IllegalStateException.class)
    public void onlyNullValues() {
        new Precedence<>().get();
    }

    @Test
    public void test() {
        new Precedence<String>()
            .candidate(() -> null)
            .candidate("test-string")
            .get(s -> {
                assertEquals("test-string", s);
                return "alternate-string";
            });
    }

}
