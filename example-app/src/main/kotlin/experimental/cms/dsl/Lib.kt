/*
 * Copyright (c) Interactive Information R & D (I2RD) LLC.
 * All Rights Reserved.
 *
 * This software is confidential and proprietary information of
 * I2RD LLC ("Confidential Information"). You shall not disclose
 * such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered
 * into with I2RD.
 */

package experimental.cms.dsl

import com.i2rd.cms.backend.BackendConfig
import com.i2rd.cms.component.miwt.MIWTPageElementModelFactory
import com.i2rd.lib.Library
import net.proteusframework.cms.CmsSite
import net.proteusframework.core.StringFactory.trimSlashes
import net.proteusframework.core.html.Element
import net.proteusframework.core.html.EntityUtil
import net.proteusframework.core.html.HTMLElement
import net.proteusframework.core.xml.GenericParser
import net.proteusframework.core.xml.TagListener
import net.proteusframework.core.xml.TagListenerConfiguration
import net.proteusframework.core.xml.XMLUtil
import net.proteusframework.ui.miwt.component.Component
import org.apache.logging.log4j.LogManager
import org.ccil.cowan.tagsoup.jaxp.SAXParserImpl
import org.xml.sax.Attributes
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter

internal fun cleanPath(path: String): String = trimSlashes(path.replace('*', '/')).replace(Regex("""/+"""), "/")

interface PathCapable {
    var path: String
    fun isWildcard(): Boolean = path.endsWith("*")
    fun getCleanPath(): String = cleanPath(path)
}

open class Identifiable(val id: String) {

    override fun toString(): String {
        return "Identifiable(id='$id')"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Identifiable) return false

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

open class IdentifiableParent<C>(id: String) : Identifiable(id) {
    val children = mutableListOf<C>()
    fun add(child: C): C = child.apply { children.add(this) }
    override fun toString(): String {
        return "IdentifiableParent(id='$id', children=$children)"
    }

}

internal class LinkTagConverter(val helper: ContentHelper) : TagListener<TagListenerConfiguration>() {

    val writer: PrintWriter get() = configuration.writer

    override fun closeStartElement() {
        writer.append('>')
    }

    override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes): Boolean {
        val ename = getElementName(uri, localName, qName)
        val convertedAttributes = mutableMapOf<String, String>()
        for (att in ATTS) {
            val value = attributes.getValue(uri, att)
            if (!value.isNullOrBlank()) {
                convertedAttributes.put(att, helper.getInternalLink(value))
                break
            }
        }
        writer.append("<").append(ename)
        for ((key, value) in convertedAttributes.entries) {
            writer.append(" ").append(key).append("=\"").append(value).append("\"")
        }
        XMLUtil.writeAttributes(writer, attributes, convertedAttributes.keys)
        if (HTMLElement.valueOf(ename).kind() == Element.Kind.VOID)
            writer.append(">")

        return true
    }


    override fun endElement(uri: String?, localName: String?, qName: String?, empty: Boolean, closedStartElement: Boolean) {
        val ename = getElementName(uri, localName, qName)
        if (HTMLElement.valueOf(ename).kind() == Element.Kind.VOID)
            return
        writer.append("</").append(ename).append(">")
    }

    override fun getSupportedTags(): Array<out String> = TAGS

    override fun characters(characters: String?, closedStartElement: Boolean): Boolean {
        if (!closedStartElement) writer.append(">")
        writer.append(characters)
        return true
    }

    private fun getElementName(uri: String?, localName: String?, qName: String?): String {
        return (if (localName.isNullOrBlank()) qName!! else localName!!).toLowerCase()
    }

    companion object {
        val TAGS = arrayOf("area", "a", "link", "form", "img", "iframe",
            "video", "source", "audio")
        val ATTS = arrayOf("href", "src", "action")
    }
}

interface ContentHelper {
    companion object {
        val logger = LogManager.getLogger(ContentHelper::class.java)!!
    }

    /**
     * Given a link, return the equivalent internal link.
     * This will convert
     * - CMS Page Paths to CMS Internal Links
     * - File references to Resource Links
     * @param link the link to convert.
     */
    fun getInternalLink(link: String): String

    /**
     * Convert XHTML links using #getInternalLink(String)
     * @param html the HTML to convert.
     */
    fun convertXHTML(html: String): String {
        val sw = StringWriter(html.length)
        val pw = PrintWriter(sw)
        val parser = GenericParser(pw)
        parser.isHtmlCompatible = false
        val config = TagListenerConfiguration(pw)
        val linkTagConverter = LinkTagConverter(this)
        linkTagConverter.init(config)
        try {
            val tagSoupParser = SAXParserImpl.newInstance(emptyMap<Any, Any>())
            XMLUtil.parseAndWrapException(tagSoupParser, parser, EntityUtil.escapeForXMLParsing(html),
                IOException::class.java)
            return sw.toString()
        } catch (e: IOException) {
            logger.warn("Unable to parse HTML.", e)
        } finally {
            linkTagConverter.destroy()
        }
        return html
    }

    fun getBackendConfig(): BackendConfig

    fun getMIWTPageElementModelFactory(): MIWTPageElementModelFactory

    fun getApplicationFunctions(): List<Component>

    fun getCmsSite(): CmsSite

    /**
     * The component identifier.
     * @param componentIdentifier the identifier. Can be looked up using the BackendConfig.
     */
    fun assignToSite(componentIdentifier: String)

    /**
     * Creates a Library, if necessary, and returns the library.
     */
    fun createLibrary(libraryName: String, libraryPath: String, libraryType: String): Library<*>?

    fun saveLibrary(library: Library<*>)
}