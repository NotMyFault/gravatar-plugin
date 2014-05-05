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
import jenkins.model.Jenkins;
import org.hamcrest.Matcher;
import org.jenkinsci.plugins.gravatar.cache.GravatarImageResolutionCacheInstance;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.JenkinsRule;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.concurrent.*;

import static com.google.common.collect.Sets.newHashSetWithExpectedSize;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;

public class UserGravatarResolverIntegrationTest {

	@Rule
	public JenkinsRule j = new JenkinsRule();
    
    private JenkinsRule.WebClient wc;

	@Before
    public void setUp() throws Exception {
		wc = j.createWebClient();
    }

	@Test
    public void testUserWithoutEmailAddressUsesDefaultImage() throws Exception {
        newUser("user-no-email");
        j.assertAllImageLoadSuccessfully(goAndWaitForLoadOfPeople());
        j.assertAllImageLoadSuccessfully(wc.goTo("user/user-no-email"));
    }

	@Test
	public void testNonExistingGravatarUsesDefaultImage() throws Exception {
        User user = newUser("user");
        user.addProperty(new Mailer.UserProperty("MyEmailAddress@example.com"));
        
        j.assertAllImageLoadSuccessfully(goAndWaitForLoadOfPeople());
        j.assertAllImageLoadSuccessfully(wc.goTo("user/user"));

        HtmlElement element = wc.goTo("user/user").getElementById("main-panel").getElementsByTagName("img").get(0);
        assertThat(element.getAttribute("src"), endsWith("user.png"));
    }

	@Test
	public void testGravatarIsUsedForUser() throws Exception {
        User user = newUser("user-e");
        user.addProperty(new Mailer.UserProperty("eramfelt@gmail.com"));
		prefetchImage(user);


		j.assertAllImageLoadSuccessfully(goAndWaitForLoadOfPeople());
        j.assertAllImageLoadSuccessfully(wc.goTo("user/user-e"));

        HtmlElement element = wc.goTo("user/user-e").getElementById("main-panel").getElementsByTagName("img").get(0);
        assertThat(element.getAttribute("src"), startsWith("http://www.gravatar.com"));
    }

	private void prefetchImage(User user) {
		GravatarImageResolutionCacheInstance.INSTANCE.urlCreatorFor(user);
	}

	@Test
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
		j.assertAllImageLoadSuccessfully(page);
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
		HtmlPage htmlPage = wc.goTo("asynchPeople");
		while(getStatus(htmlPage).isDisplayed()) {
			//the asynch part has not yet finished, so we wait.
			Thread.sleep(500);
		}
		return htmlPage;
	}

	private HtmlElement getStatus(HtmlPage htmlPage) {
		final HtmlElement statusById = htmlPage.getElementById("status");
		if(statusById != null) {
			return statusById;
		}
		final ListIterator<HtmlElement> tablesOnPage = htmlPage.getElementsByTagName("table").listIterator();
		while (tablesOnPage.hasNext()) {
			HtmlElement next = tablesOnPage.next();
			if("progress-bar".equalsIgnoreCase(next.getAttribute("class"))) {
				return next;
			}
		}
		return null;
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