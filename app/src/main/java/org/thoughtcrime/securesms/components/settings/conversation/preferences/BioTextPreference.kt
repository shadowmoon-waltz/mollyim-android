package org.thoughtcrime.securesms.components.settings.conversation.preferences

import android.content.ClipData
import android.content.Context
import android.text.SpannableStringBuilder
import android.view.View
import android.widget.TextView
import android.widget.Toast
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.PreferenceModel
import org.thoughtcrime.securesms.fonts.SignalSymbols
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.ContextUtil
import org.thoughtcrime.securesms.util.ServiceUtil
import org.thoughtcrime.securesms.util.SpanUtil
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.ThemeUtil
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder

/**
 * Renders name, description, about, etc. for a given group or recipient.
 */
object BioTextPreference {

  fun register(adapter: MappingAdapter) {
    adapter.registerFactory(RecipientModel::class.java, LayoutFactory(::RecipientViewHolder, R.layout.conversation_settings_bio_preference_item))
    adapter.registerFactory(GroupModel::class.java, LayoutFactory(::GroupViewHolder, R.layout.conversation_settings_bio_preference_item))
  }

  abstract class BioTextPreferenceModel<T : BioTextPreferenceModel<T>> : PreferenceModel<T>() {
    abstract fun getHeadlineText(context: Context): CharSequence
    abstract fun getSubhead0Text(context: Context): String?
    abstract fun getSubhead1Text(context: Context): String?
    abstract fun getSubhead2Text(): String?
    abstract fun getSubhead2ExtraText(context: Context): String?

    open val onHeadlineClickListener: (() -> Unit)? = null
  }

  class RecipientModel(
    private val recipient: Recipient,
    private val linkedDevices: Int?,
    override val onHeadlineClickListener: (() -> Unit)?
  ) : BioTextPreferenceModel<RecipientModel>() {

    override fun getHeadlineText(context: Context): CharSequence {
      val name = if (recipient.isSelf) {
        context.getString(R.string.note_to_self)
      } else {
        recipient.getDisplayName(context)
      }

      if (!recipient.showVerified && !recipient.isIndividual) {
        return name
      }

      return SpannableStringBuilder(name).apply {
        if (recipient.showVerified) {
          SpanUtil.appendSpacer(this, 8)
          SpanUtil.appendCenteredImageSpanWithoutSpace(this, ContextUtil.requireDrawable(context, R.drawable.ic_official_28), 28, 28)
        } else if (recipient.isSystemContact) {
          val systemContactGlyph = SignalSymbols.getSpannedString(
            context,
            SignalSymbols.Weight.BOLD,
            SignalSymbols.Glyph.PERSON_CIRCLE
          ).let {
            SpanUtil.ofSize(it, 20)
          }

          append(" ")
          append(systemContactGlyph)
        }

        if (recipient.isIndividual && !recipient.isSelf) {
          val isLtr = ViewUtil.isLtr(context)
          val chevronGlyph = SignalSymbols.getSpannedString(
            context,
            SignalSymbols.Weight.BOLD,
            if (isLtr) SignalSymbols.Glyph.CHEVRON_RIGHT else SignalSymbols.Glyph.CHEVRON_LEFT
          ).let {
            SpanUtil.ofSize(it, 24)
          }.let {
            SpanUtil.color(ThemeUtil.getThemedColor(context, com.google.android.material.R.attr.colorOutline), it)
          }

          if (isLtr) {
            append(" ")
            append(chevronGlyph)
          } else {
            insert(0, " ")
            insert(0, chevronGlyph)
          }
        }
      }
    }

    override fun getSubhead0Text(context: Context): String? {
      if (!recipient.isReleaseNotes && !recipient.isSelf && TextSecurePreferences.isAlsoShowProfileName(context)) {
        return recipient.getDisplayName2(context).let { return if (!it.isEmpty()) it else null }
      } else {
        return null
      }
    }

    override fun getSubhead1Text(context: Context): String? {
      return if (recipient.isReleaseNotes) {
        context.getString(R.string.ReleaseNotes__signal_release_notes_and_news)
      } else {
        recipient.combinedAboutAndEmoji
      }
    }

    override fun getSubhead2Text(): String? = null

    override fun getSubhead2ExtraText(context: Context): String? {
      if (linkedDevices == null) {
        return null
      }
      return if (linkedDevices > 0) {
        context.resources.getQuantityString(R.plurals.BioTextPreference_n_linked_devices, linkedDevices, linkedDevices)
      } else {
        context.getString(R.string.BioTextPreference_no_linked_devices)
      }
    }

    override fun areContentsTheSame(newItem: RecipientModel): Boolean {
      return super.areContentsTheSame(newItem) && newItem.recipient.hasSameContent(recipient)
    }

    override fun areItemsTheSame(newItem: RecipientModel): Boolean {
      return newItem.recipient.id == recipient.id
    }
  }

  class GroupModel(
    val groupTitle: String,
    val groupMembershipDescription: String?
  ) : BioTextPreferenceModel<GroupModel>() {
    override fun getHeadlineText(context: Context): CharSequence = groupTitle

    override fun getSubhead0Text(context: Context): String? = null

    override fun getSubhead1Text(context: Context): String? = groupMembershipDescription

    override fun getSubhead2Text(): String? = null

    override fun getSubhead2ExtraText(context: Context): String? = null

    override fun areContentsTheSame(newItem: GroupModel): Boolean {
      return super.areContentsTheSame(newItem) &&
        groupTitle == newItem.groupTitle &&
        groupMembershipDescription == newItem.groupMembershipDescription
    }

    override fun areItemsTheSame(newItem: GroupModel): Boolean {
      return true
    }
  }

  private abstract class BioTextViewHolder<T : BioTextPreferenceModel<T>>(itemView: View) : MappingViewHolder<T>(itemView) {

    private val headline: TextView = itemView.findViewById(R.id.bio_preference_headline)
    private val subhead0: TextView = itemView.findViewById(R.id.bio_preference_subhead_0)
    private val subhead1: TextView = itemView.findViewById(R.id.bio_preference_subhead_1)
    protected val subhead2: TextView = itemView.findViewById(R.id.bio_preference_subhead_2)
    protected val subhead2extra: TextView = itemView.findViewById(R.id.bio_preference_subhead_2_extra)

    override fun bind(model: T) {
      headline.text = model.getHeadlineText(context)

      val clickListener = model.onHeadlineClickListener
      if (clickListener != null) {
        headline.setOnClickListener { clickListener() }
      }

      model.getSubhead0Text(context).let {
        subhead0.text = it
        subhead0.visibility = if (it == null) View.GONE else View.VISIBLE
      }

      model.getSubhead1Text(context).let {
        subhead1.text = it
        subhead1.visibility = if (it == null) View.GONE else View.VISIBLE
      }

      model.getSubhead2Text().let {
        subhead2.text = it
        subhead2.visibility = if (it == null) View.GONE else View.VISIBLE
      }

      model.getSubhead2ExtraText(context).let {
        subhead2extra.text = it
        subhead2extra.visibility = if (it == null) View.GONE else View.VISIBLE
      }
    }
  }

  private class RecipientViewHolder(itemView: View) : BioTextViewHolder<RecipientModel>(itemView) {
    override fun bind(model: RecipientModel) {
      super.bind(model)

      val phoneNumber = model.getSubhead2Text()
      if (!phoneNumber.isNullOrEmpty()) {
        subhead2.setOnLongClickListener {
          val clipboardManager = ServiceUtil.getClipboardManager(context)
          clipboardManager.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.ConversationSettingsFragment__phone_number), subhead2.text.toString()))
          Toast.makeText(context, R.string.ConversationSettingsFragment__copied_phone_number_to_clipboard, Toast.LENGTH_SHORT).show()
          true
        }
      } else {
        subhead2.setOnLongClickListener(null)
      }
    }
  }

  private class GroupViewHolder(itemView: View) : BioTextViewHolder<GroupModel>(itemView)
}
