/*
 * The MIT License
 * 
 * Copyright (c) 2011, Erik Ramfelt
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkinsci.plugins.gravatar;

import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.User;
import hudson.tasks.Mailer;
import org.hamcrest.Matcher;
import org.jenkinsci.plugins.gravatar.cache.GravatarImageResolutionCacheInstance;
import org.jvnet.hudson.test.HudsonTestCase;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

import static com.google.common.collect.Sets.newHashSetWithExpectedSize;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;

public class UserGravatarResolverIntegrationTest extends HudsonTestCase {
    
    private WebClient wc;

    public void setUp() throws Exception {
        super.setUp();
        wc = createWebClient();
    }
    
    public void testUserWithoutEmailAddressUsesDefaultImage() throws Exception {
        newUser("user-no-email");
        assertAllImageLoadSuccessfully(goAndWaitForLoadOfPeople());
        assertAllImageLoadSuccessfully(wc.goTo("/user/user-no-email"));
    }
    
    public void testNonExistingGravatarUsesDefaultImage() throws Exception {
        User user = newUser("user");
        user.addProperty(new Mailer.UserProperty("MyEmailAddress@example.com"));
        
        assertAllImageLoadSuccessfully(goAndWaitForLoadOfPeople());
        assertAllImageLoadSuccessfully(wc.goTo("/user/user"));

        HtmlElement element = wc.goTo("/user/user").getElementById("main-panel").getElementsByTagName("img").get(0);
        assertThat(element.getAttribute("src"), endsWith("user.png"));
    }

	public void testGravatarIsUsedForUser() throws Exception {
        User user = newUser("user-e");
        user.addProperty(new Mailer.UserProperty("eramfelt@gmail.com"));
		prefetchImage(user);


		assertAllImageLoadSuccessfully(goAndWaitForLoadOfPeople());
        assertAllImageLoadSuccessfully(wc.goTo("/user/user-e"));

        HtmlElement element = wc.goTo("/user/user-e").getElementById("main-panel").getElementsByTagName("img").get(0);
        assertThat(element.getAttribute("src"), startsWith("http://www.gravatar.com"));
    }

	private void prefetchImage(User user) {
		GravatarImageResolutionCacheInstance.INSTANCE.urlCreatorFor(user);
	}

	public void testManyManyUsersWillNotBlockLoadingOfUsersPage() throws Exception {
		final int howMany = 1000;

		Callable<HtmlPage> c = new Callable<HtmlPage>() {
			public HtmlPage call() throws Exception {
				createManyManyUsers(howMany);
				return goAndWaitForLoadOfPeople();
			}
		};
		ExecutorService executorService = Executors.newSingleThreadExecutor();
		Future<HtmlPage> pageFuture = executorService.submit(c);
		HtmlPage page = pageFuture.get(60, TimeUnit.SECONDS); //if it takes longer than this we consider it to BLOCK!
		assertAllImageLoadSuccessfully(page);
		assertThatUserCount(page, equalTo(howMany));

	}

	private void assertThatUserCount(HtmlPage page, Matcher<Integer> integerMatcher) {
		List<HtmlAnchor> userLinks = page.selectNodes("//a[contains(@href,'/user/')]");
		Set<String> targets = newHashSetWithExpectedSize(userLinks.size());
		for (HtmlAnchor userLink : userLinks) {
			targets.add(userLink.getHrefAttribute());
		}
		assertThat(targets.size(), integerMatcher);
	}

	private HtmlPage goAndWaitForLoadOfPeople() throws InterruptedException, IOException, SAXException {
		HtmlPage htmlPage = wc.goTo("/asynchPeople");
		while(htmlPage.getElementById("status").isDisplayed()) {
			//the asynch part has not yet finished, so we wait.
			Thread.sleep(500);
		}
		return htmlPage;
	}

	private void createManyManyUsers(int howMany) throws IOException {
		for (int i= 0; i< howMany; i++) {
			User user = newUser("manymanyuser" + i);
			user.addProperty(new Mailer.UserProperty("user"+i+"@gmail.com"));
		}
	}

	private User newUser(String userId) {
		return User.get(userId, true, Collections.emptyMap());
	}

}