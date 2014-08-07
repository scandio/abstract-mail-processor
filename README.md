#Abstract Mail Processor

Abstract Confluence plugin to use as a base for developing a plugin that needs to process mails from a mail account.

This is a fork of the [Mail to News](https://github.com/stimmt/Confluence-Mail-to-News-Plugin) plugin.

## How to use

1. Fork this repository
2. Change groupId, artifactId, name and description
3. Implement a processor extending the class AbstractMailProcessorJob
4. Swap your class with with the Mail2NewsJob class in the job module in the atlassian-plugin.xml
