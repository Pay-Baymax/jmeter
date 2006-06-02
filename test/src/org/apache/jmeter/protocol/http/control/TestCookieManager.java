/*
 * Copyright 2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */

package org.apache.jmeter.protocol.http.control;

import java.net.URL;

import org.apache.jmeter.junit.JMeterTestCase;
import org.apache.jmeter.protocol.http.sampler.HTTPNullSampler;
import org.apache.jmeter.protocol.http.sampler.HTTPSamplerBase;
import org.apache.jmeter.threads.JMeterContext;
import org.apache.jmeter.threads.JMeterContextService;

public class TestCookieManager extends JMeterTestCase {
        private CookieManager man = null;

        public TestCookieManager(String name) {
            super(name);
        }

        private JMeterContext jmctx = null;

        public void setUp() throws Exception {
            super.setUp();
            jmctx = JMeterContextService.getContext();
            man = new CookieManager();
            man.setThreadContext(jmctx);
        }

        public void testRemoveCookie() throws Exception {
            man.setThreadContext(jmctx);
            Cookie c = new Cookie("id", "me", "127.0.0.1", "/", false, 0);
            man.add(c);
            assertEquals(1, man.getCookieCount());
            // This should be ignored, as there is no value
            Cookie d = new Cookie("id", "", "127.0.0.1", "/", false, 0);
            man.add(d);
            assertEquals(0, man.getCookieCount());
            man.add(c);
            man.add(c);
            assertEquals(1, man.getCookieCount());
            Cookie e = new Cookie("id", "me2", "127.0.0.1", "/", false, 0);
            man.add(e);
            assertEquals(1, man.getCookieCount());
        }

        public void testSendCookie() throws Exception {
            man.add(new Cookie("id", "value", "jakarta.apache.org", "/", false, 9999999999L));
            HTTPSamplerBase sampler = new HTTPNullSampler();
            sampler.setDomain("jakarta.apache.org");
            sampler.setPath("/index.html");
            sampler.setMethod(HTTPSamplerBase.GET);
            assertNotNull(man.getCookieHeaderForURL(sampler.getUrl()));
        }

        public void testSendCookie2() throws Exception {
            man.add(new Cookie("id", "value", ".apache.org", "/", false, 9999999999L));
            HTTPSamplerBase sampler = new HTTPNullSampler();
            sampler.setDomain("jakarta.apache.org");
            sampler.setPath("/index.html");
            sampler.setMethod(HTTPSamplerBase.GET);
            assertNotNull(man.getCookieHeaderForURL(sampler.getUrl()));
        }

        /**
         * Test that the cookie domain field is actually handled as browsers do
         * (i.e.: host X matches domain .X):
         */
        public void testDomainHandling() throws Exception {
            URL url = new URL("http://jakarta.apache.org/");
            man.addCookieFromHeader("test=1;domain=.jakarta.apache.org", url);
            assertNotNull(man.getCookieHeaderForURL(url));
        }

        /**
         * Test that we won't be tricked by similar host names (this was a past
         * bug, although it never got reported in the bug database):
         */
        public void testSimilarHostNames() throws Exception {
            URL url = new URL("http://ache.org/");
            man.addCookieFromHeader("test=1", url);
            url = new URL("http://jakarta.apache.org/");
            assertNull(man.getCookieHeaderForURL(url));
        }

        // Test session cookie is returned
        public void testSessionCookie() throws Exception {
            URL url = new URL("http://a.b.c/");
            man.addCookieFromHeader("test=1", url);
            String s = man.getCookieHeaderForURL(url);
            assertNotNull(s);
            assertEquals("test=1", s);
        }

        // Bug 2063
        public void testCookieWithEquals() throws Exception {
            URL url = new URL("http://a.b.c/");
            man.addCookieFromHeader("NSCP_USER_LOGIN1_NEW=SHA=xxxxx", url);
            String s = man.getCookieHeaderForURL(url);
            assertNotNull(s);
            assertEquals("NSCP_USER_LOGIN1_NEW=SHA=xxxxx", s);
            Cookie c=man.get(0);
            assertEquals("NSCP_USER_LOGIN1_NEW",c.getName());
            assertEquals("SHA=xxxxx",c.getValue());
        }

        // Test Old cookie is not returned
        public void testOldCookie() throws Exception {
            URL url = new URL("http://a.b.c/");
            man.addCookieFromHeader("test=1; expires=Mon, 01-Jan-1990 00:00:00 GMT", url);
            String s = man.getCookieHeaderForURL(url);
            assertNull(s);
        }

        // Test New cookie is returned
        public void testNewCookie() throws Exception {
            URL url = new URL("http://a.b.c/");
            man.addCookieFromHeader("test=1; expires=Mon, 01-Jan-2990 00:00:00 GMT", url);
            assertEquals(1,man.getCookieCount());
            String s = man.getCookieHeaderForURL(url);
            assertNotNull(s);
            assertEquals("test=1", s);
        }

        // Test multi-cookie header handling
        public void testCookies1() throws Exception {
            URL url = new URL("http://a.b.c.d/testCookies1");
            man.addCookieFromHeader("test1=1; comment=\"how,now\", test2=2; version=1", url);
            assertEquals(2,man.getCookieCount());
            String s = man.getCookieHeaderForURL(url);
            assertNotNull(s);
            assertEquals("test1=1; test2=2", s);
        }
        
        public void testCookies2() throws Exception {
            URL url = new URL("https://a.b.c.d/testCookies2");
            man.addCookieFromHeader("test1=1;secure, test2=2;secure", url);
            assertEquals(2,man.getCookieCount());
            String s = man.getCookieHeaderForURL(url);
            assertNotNull(s);
            assertEquals("test1=1; test2=2", s);
        }

        // Test duplicate cookie handling
        public void testDuplicateCookie() throws Exception {
            URL url = new URL("http://a.b.c/");
            man.addCookieFromHeader("test=1", url);
            String s = man.getCookieHeaderForURL(url);
            assertNotNull(s);
            assertEquals("test=1", s);
            man.addCookieFromHeader("test=2", url);
            s = man.getCookieHeaderForURL(url);
            assertNotNull(s);
            assertEquals("test=2", s);
        }
        public void testDuplicateCookie2() throws Exception {
            URL url = new URL("http://a.b.c/");
            man.addCookieFromHeader("test=1", url);
            man.addCookieFromHeader("test2=a", url);
            String s = man.getCookieHeaderForURL(url);
            assertNotNull(s);
            assertEquals("test=1; test2=a", s); // Assumes some kind of list is used
            man.addCookieFromHeader("test=2", url);
            man.addCookieFromHeader("test3=b", url);
            s = man.getCookieHeaderForURL(url);
            assertNotNull(s);
            assertEquals("test2=a; test=2; test3=b", s);// Assumes some kind of list is use
            // If not using a list that retains the order, then the asserts would need to change
        }
        
         
 		/** Tests missing cookie path for a trivial URL fetch from the domain 
 		 *  Note that this fails prior to a fix for BUG 38256
 		 */
 		public void testMissingPath0() throws Exception {
 			URL url = new URL("http://d.e.f/goo.html");
 			man.addCookieFromHeader("test=moo", url);
 			String s = man.getCookieHeaderForURL(new URL("http://d.e.f/"));
 			assertNotNull(s);
 			assertEquals("test=moo", s);
 		}
 		
 		/** Tests missing cookie path for a non-trivial URL fetch from the 
 		 *  domain.  Note that this fails prior to a fix for BUG 38256
 		 */
 		public void testMissingPath1() throws Exception {
 			URL url = new URL("http://d.e.f/moo.html");
 			man.addCookieFromHeader("test=moo", url);
 			String s = man.getCookieHeaderForURL(new URL("http://d.e.f/goo.html"));
 			assertNotNull(s);
 			assertEquals("test=moo", s);
 		}
 		
 		/** Tests explicit root path with a trivial URL fetch from the domain */
 		public void testRootPath0() throws Exception {
 			URL url = new URL("http://d.e.f/goo.html");
 			man.addCookieFromHeader("test=moo;path=/", url);
 			String s = man.getCookieHeaderForURL(new URL("http://d.e.f/"));
 			assertNotNull(s);
 			assertEquals("test=moo", s);
 		}
 		
 		/** Tests explicit root path with a non-trivial URL fetch from the domain */
 		public void testRootPath1() throws Exception {
 			URL url = new URL("http://d.e.f/moo.html");
 			man.addCookieFromHeader("test=moo;path=/", url);
 			String s = man.getCookieHeaderForURL(new URL("http://d.e.f/goo.html"));
 			assertNotNull(s);
 			assertEquals("test=moo", s);
 		}
        
        // Test cookie matching
        public void testCookieMatching() throws Exception {
            URL url = new URL("http://a.b.c:8080/TopDir/fred.jsp");
            man.addCookieFromHeader("ID=abcd; Path=/TopDir", url);
            String s = man.getCookieHeaderForURL(url);
            assertNotNull(s);
            assertEquals("ID=abcd", s);

            url = new URL("http://a.b.c:8080/other.jsp");
            s=man.getCookieHeaderForURL(url);
            assertNull(s);
            
            url = new URL("http://a.b.c:8080/TopDir/suub/another.jsp");
            s=man.getCookieHeaderForURL(url);
            assertNotNull(s);
            
            url = new URL("http://a.b.c:8080/TopDir");
            s=man.getCookieHeaderForURL(url);
            assertNotNull(s);
            
            url = new URL("http://a.b.d/");
            s=man.getCookieHeaderForURL(url);
            assertNull(s);
        }

}
