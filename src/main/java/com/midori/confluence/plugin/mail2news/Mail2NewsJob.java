/**
 * The mail2news confluence plugin, job module.
 * This is the job which is periodically executed to
 * check for new email messages in a specified account
 * and add them to the news of a space.
 *
 * This software is licensed under the BSD license.
 *
 * Copyright (c) 2008, Liip AG
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice,
 *   this list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 * - Neither the name of Liip AG nor the names of its contributors may be used
 *   to endorse or promote products derived from this software without
 *   specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * @author   Roman Schlegel <roman@liip.ch>
 * @version  $Id$
 * @package  com.midori.confluence.plugin.mail2news.mail2news
 */

package com.midori.confluence.plugin.mail2news;

import com.atlassian.confluence.pages.Attachment;
import com.atlassian.confluence.pages.BlogPost;
import com.atlassian.confluence.spaces.Space;
import com.atlassian.confluence.user.AuthenticatedUserThreadLocal;
import com.atlassian.confluence.util.GeneralUtil;
import com.atlassian.user.User;
import com.atlassian.user.search.SearchResult;
import com.atlassian.user.search.page.Pager;
import de.scandio.confluence.plugins.abstractmailprocessor.AbstractMailProcessorJob;

import javax.mail.Message;
import javax.mail.MessagingException;
import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

public class Mail2NewsJob extends AbstractMailProcessorJob {


	/**
	 * Create a blog post from the content and the attachments retrieved from a
	 * mail message.
	 * There are only two parameters, the other necessary parameters are global
	 * variables.
	 *
	 * @param m The message which to publish as a blog post.
	 * @throws MessagingException Throws a MessagingException if something goes wrong when getting attributes from the message.
	 */
    @Override
	protected void process(Message m) throws MessagingException
	{
        Space space;
        try {
            space = getSpaceFromAddress(m);
        }
        catch (Exception e)
        {
            this.log.error("Could not get space from message: " + e.getMessage());
            return;
        }

		/* create the blogPost and add values */
		BlogPost blogPost = new BlogPost();
		/* set the creation date of the blog post to the current date */
		blogPost.setCreationDate(new Date());
		/* set the space where to save the blog post */
		blogPost.setSpace(space);
		/* if the gallery macro is set and the post contains an image add the macro */
		MailConfiguration config = configurationManager.getMailConfiguration();
		if (config.getGallerymacro())
		{
			/* gallery macro is set */
			if (containsImage)
			{
				/* post contains an image */
				/* add the macro */
				blogEntryContent = blogEntryContent.concat("{gallery}");
			}
		}
		/* set the blog post content */
		if (blogEntryContent != null)
		{
			blogPost.setBodyAsString(blogEntryContent);
		}
		else
		{
			blogPost.setBodyAsString("");
		}
		/* set the title of the blog post */
		String title = m.getSubject();
		/* check for illegal characters in the title and replace them with a space */
		/* could be replaced with a regex */
		/* Only needed for Confluence < 4.1 */
		String version = GeneralUtil.getVersionNumber();
		if (!Pattern.matches("^4\\.[1-9]+.*$", version)) {
			char[] illegalCharacters = {':', '@', '/', '%', '\\', '&', '!', '|', '#', '$', '*', ';', '~', '[', ']', '(', ')', '{', '}', '<', '>', '.'};
			for (int i = 0; i < illegalCharacters.length; i++)
			{
				if (title.indexOf(illegalCharacters[i]) != -1)
				{
					title = title.replace(illegalCharacters[i], ' ');
				}
			}
		}
		blogPost.setTitle(title);

		/* set creating user */
		String creatorEmail = getEmailAddressFromMessage(m);

		String creatorName = null;
		User creator = null;
		if (creatorEmail != "")
		{
			SearchResult sr = userAccessor.getUsersByEmail(creatorEmail);

			Pager p = sr.pager();
			List l = p.getCurrentPage();

			if (l.size() == 1)
			{
				/* found a matching user for the email address of the sender */
				creator = (User)l.get(0);
				creatorName = creator.getName();
			}
		}

		//this.log.info("creatorName: " + creatorName);
		//this.log.info("creator: " + creator);
		blogPost.setCreatorName(creatorName);

		if (creator != null)
		{
			AuthenticatedUserThreadLocal.setUser(creator);
		}
		else
		{
			//this.log.info("Resetting authenticated user.");
			AuthenticatedUserThreadLocal.setUser(null);
		}

		/* save the blog post */
		pageManager.saveContentEntity(blogPost, null);

		/* set attachments of this blog post */
		/* we have to save the blog post before we can add the
		 * attachments, because attachments need to be attached to
		 * a content. */
		Attachment[] a = new Attachment[attachments.size()];
		a = (Attachment[])attachments.toArray(a);
		for (int j = 0; j < a.length; j++)
		{
			InputStream is = (InputStream)attachmentsInputStreams.get(j);

			/* save the attachment */
			try
			{
				/* set the creator of the attachment */
				a[j].setCreatorName(creatorName);
				/* set the content of this attachment to the newly saved blog post */
				a[j].setContent(blogPost);
				attachmentManager.saveAttachment(a[j], null, is);
			}
			catch (Exception e)
			{
				this.log.error("Could not save attachment: " + e.getMessage(), e);
				/* skip this attachment */
				continue;
			}

			/* add the attachment to the blog post */
			blogPost.addAttachment(a[j]);
		}
	}
}
