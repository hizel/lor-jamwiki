package org.vqwiki.parser.alt;


/*
Very Quick Wiki - WikiWikiWeb clone
Copyright (C) 2001-2003 Gareth Cronin

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU Lesser General Public License as published by
the Free Software Foundation; either version 2 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU Lesser General Public License
along with this program (gpl.txt); if not, write to the Free Software
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
*/

import java.io.*;
import java.util.List;
import java.util.ArrayList;

import org.apache.log4j.Logger;
import org.vqwiki.parser.Lexer;
import org.vqwiki.Environment;
import org.vqwiki.WikiBase;
import org.vqwiki.utils.Utilities;
import org.vqwiki.utils.JSPUtils;

%%

%public
%type String
%unicode
%implements	org.vqwiki.parser.Lexer
%class BackLinkLex

%init{
	yybegin( NORMAL );
%init}

%eofval{
	return null;
%eofval}

%{
	protected static Logger logger = Logger.getLogger( BackLinkLex.class );
  protected String virtualWiki;
  private List links = new ArrayList();

  public void setVirtualWiki( String vWiki ){
    this.virtualWiki = vWiki;
  }

	protected boolean ignoreWikiname( String name ){
	  return VQWikiParser.doIgnoreWikiname(name);
	}

	public List getLinks(){
	  return this.links;
	}

	private void addLink(String link){
	  logger.debug("adding link: '" + link + "'");
	  this.links.add(link);
	}
%}

whitespace = ([\t\ \r\n])
notbacktick_tabcrlf = ([^`\t\r\n])
notsquares_tabcrlf = ([^\]\t\r\n])
notbacktick_notsquares_tabcrlf = ([^`\]\t\r\n])
wikiname = (([:uppercase:]+[:lowercase:]+)([:uppercase:]+[:lowercase:]+)+)
topic = ({wikiname})
topicbacktick = (`{notbacktick_tabcrlf}+`)
topicbackticktail = (`{notbacktick_tabcrlf}+`([:letter:])+)
topicsquarebracket = (\[\[{notsquares_tabcrlf}+\]\])
topicsquarebrackettail = (\[\[{notsquares_tabcrlf}+\]\]([:letter:])+)
prettytopicsquarebracket = (\[\[{notsquares_tabcrlf}+\|{notsquares_tabcrlf}+\]\])
prettytopicsquarebrackettail = (\[\[{notsquares_tabcrlf}+\|{notsquares_tabcrlf}+\]\]([:letter:])+)
protocols = (http|ftp|mailto|news|https|telnet|file)
extlinkchar = ([^\t\ \r\n\<\>])
prettyextlinkchar = ([^\t\r\n\<\>\|])
hyperlink = ({protocols}:{extlinkchar}+)
framedhyperlink = (\[({protocols}:{prettyextlinkchar}+)\])
prettyhyperlink = (\[({protocols}:{prettyextlinkchar}+)\|{notsquares_tabcrlf}+\])
image = ({hyperlink}(\.gif|\.jpg|\.png|\.jpeg|\.GIF|\.JPG|\.PNG|\.JPEG|\.bmp|\.BMP))
extlink = (([:letter:]|[:digit:])+:{extlinkchar}+)
framedextlink = (\[([:letter:]|[:digit:])+:{notbacktick_notsquares_tabcrlf}+\])
noformat = (__)
externalstart = (\[<[A-Za-z]+>\])
externalend = (\[<\/[A-Za-z]+>\])
attachment = (attach:{extlinkchar}+)
attachment2 = (attach:\".+\")
imageattachment = (attach:{extlinkchar}+(\.gif|\.jpg|\.png|\.jpeg|\.GIF|\.JPG|\.PNG|\.JPEG|\.bmp|\.BMP))
imageattachment2 = (attach:\".+(\.gif|\.jpg|\.png|\.jpeg|\.GIF|\.JPG|\.PNG|\.JPEG|\.bmp|\.BMP)\")
// TODO: edit:-topics.

%state NORMAL, OFF, PRE, EXTERNAL

%%
<NORMAL>\\{noformat}	{
  logger.debug( "escaped double backslash" );
  return "__";
}

<NORMAL>{noformat}	{
  logger.debug( "format off" );
  yybegin( OFF );
}

<OFF>{noformat}	{
  logger.debug( "format on" );
  yybegin( NORMAL );
}

<NORMAL, PRE>{externalstart} {
  logger.debug( "external" );
  yybegin( EXTERNAL );
}

<EXTERNAL>{externalend} {
  logger.debug( "external end");
  yybegin( NORMAL );
}

<NORMAL>(<pre>) {
  logger.debug( "@@@@{newline} entering PRE" );
  yybegin( PRE );
}

<PRE>(<\/pre>) {
  logger.debug( "{newline}x2 leaving pre" );
  yybegin( NORMAL );
}

<NORMAL>{image}	{
  logger.debug( "{image}" );
}

<NORMAL>{hyperlink}	{
  logger.debug( "{hyperlink}" );
}

<NORMAL>{framedhyperlink}	{
  logger.debug( "{framedhyperlink}" );
}

<NORMAL>{prettyhyperlink}	{
  logger.debug( "{prettyhyperlink}" );
}

<NORMAL>{prettytopicsquarebracket}	{
  logger.debug( "{prettytopicsquarebracket} '" + yytext() + "'" );
  String input = yytext();
  int position = input.indexOf('|');
  
  String link = null;
  link = input.substring(2, position).trim();
  
  addLink(link);
}

<NORMAL>{prettytopicsquarebrackettail}
{
  logger.debug( "{prettytopicsquarebrackettail} '" + yytext() + "'" );
  String input = yytext();
  int position = input.indexOf('|');
  
  String link = null;
  link = input.substring(2, position).trim();
  addLink(link);
}

<NORMAL>{topic} {
  logger.debug( "{topic} '" + yytext() + "'" );
  String link = yytext();
  if( !ignoreWikiname( link ) ){
    addLink(link.trim());
  }
}

<NORMAL>{topicbacktick} {
  logger.debug( "{topicbacktick} '" + yytext() + "'" );
  if( !Environment.getBooleanValue(Environment.PROP_PARSER_ALLOW_BACK_TICK) ) {
    logger.debug( "No back-tick links allowed" );
    return yytext();
  }
  String link = yytext();
  link = link.substring(1);
  link = link.substring( 0, link.length() - 1).trim();
  addLink(link);
}

<NORMAL>{topicbackticktail} {
  logger.debug( "{topicbackticktail} '" + yytext() + "'" );
  if( !Environment.getBooleanValue(Environment.PROP_PARSER_ALLOW_BACK_TICK) ) {
    logger.debug( "No back-tick links allowed" );
    return yytext();
  }
  String link = yytext();
  link = link.substring( 0, link.indexOf('`')).trim();
  addLink(link);
}

<NORMAL>{topicsquarebracket} {
  logger.debug( "{topicsquarebracket} '" + yytext() + "'");
  String link = yytext();
  link = link.substring(2);
  link = link.substring( 0, link.length() - 2).trim();
  addLink(link);
}

<NORMAL>{topicsquarebrackettail} {
  logger.debug( "{topicsquarebrackettail} '" + yytext() + "'");
  String link = yytext();
  link = link.substring(2);
  link = link.substring( 0, link.indexOf("]]")).trim();
  addLink(link);
}

<NORMAL>{imageattachment2} {
  logger.debug( "{imageattachment2}" );
}

<NORMAL>{imageattachment} {
  logger.debug( "{imageattachment}" );
}

<NORMAL>{attachment2} {
 logger.debug( "{attachment}" );
}


<NORMAL>{attachment} {
 logger.debug( "{attachment}" );
}

<NORMAL>{extlink} {
 logger.debug( "{extlink}" );
}

<NORMAL>{framedextlink} {
 logger.debug( "{extlink2}" );
}

<NORMAL, OFF, PRE, EXTERNAL>{whitespace} {
  return yytext();
}

<NORMAL, OFF, PRE, EXTERNAL>.  {
 //logger.debug( ". (" + yytext() + ")" );
 return yytext();
}
