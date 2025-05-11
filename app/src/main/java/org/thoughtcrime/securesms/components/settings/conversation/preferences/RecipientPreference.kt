package org.thoughtcrime.securesms.components.settings.conversation.preferences

import android.text.SpannableStringBuilder
import android.view.View
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.BadgeImageView
import org.thoughtcrime.securesms.components.AvatarImageView
import org.thoughtcrime.securesms.components.settings.PreferenceModel
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.ContextUtil
import org.thoughtcrime.securesms.util.SpanUtil
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.ThemeUtil
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder
import org.thoughtcrime.securesms.util.visible

/**
 * Renders a Recipient as a row item with an icon, avatar, status, and admin state
 */
object RecipientPreference {

  fun register(adapter: MappingAdapter) {
    adapter.registerFactory(Model::class.java, LayoutFactory(::ViewHolder, R.layout.group_recipient_list_item))
  }

  class Model(
    val recipient: Recipient,
    val isAdmin: Boolean = false,
    val lifecycleOwner: LifecycleOwner? = null,
    val onClick: (() -> Unit)? = null
  ) : PreferenceModel<Model>() {
    override fun areItemsTheSame(newItem: Model): Boolean {
      return recipient.id == newItem.recipient.id
    }

    override fun areContentsTheSame(newItem: Model): Boolean {
      return super.areContentsTheSame(newItem) &&
        recipient.hasSameContent(newItem.recipient) &&
        isAdmin == newItem.isAdmin
    }
  }

  class ViewHolder(itemView: View) : MappingViewHolder<Model>(itemView) {
    private val avatar: AvatarImageView = itemView.findViewById(R.id.recipient_avatar)
    private val name: TextView = itemView.findViewById(R.id.recipient_name)
    private val about: TextView? = itemView.findViewById(R.id.recipient_about)
    private val admin: View? = itemView.findViewById(R.id.admin)
    private val badge: BadgeImageView = itemView.findViewById(R.id.recipient_badge)

    private var recipient: Recipient? = null

    private val recipientObserver = object : Observer<Recipient> {
      override fun onChanged(recipient: Recipient) {
        onRecipientChanged(recipient)
      }
    }

    override fun bind(model: Model) {
      if (model.onClick != null) {
        itemView.setOnClickListener { model.onClick.invoke() }
      } else {
        itemView.setOnClickListener(null)
      }

      if (model.lifecycleOwner != null) {
        observeRecipient(model.lifecycleOwner, model.recipient)
      } else {
        onRecipientChanged(model.recipient)
      }

      admin?.visible = model.isAdmin
    }

    override fun onViewRecycled() {
      unbind()
    }

    private fun onRecipientChanged(recipient: Recipient) {
      avatar.setRecipient(recipient)
      badge.setBadgeFromRecipient(recipient)
      name.text = if (recipient.isSelf) {
        context.getString(R.string.Recipient_you)
      } else {
        if (recipient.isSystemContact) {
          SpannableStringBuilder(recipient.getDisplayName(context)).apply {
            val drawable = ContextUtil.requireDrawable(context, R.drawable.symbol_person_circle_24).apply {
              setTint(ThemeUtil.getThemedColor(context, com.google.android.material.R.attr.colorOnSurface))
            }
            SpanUtil.appendCenteredImageSpan(this, drawable, 16, 16)
          }
        } else {
          recipient.getDisplayName(context)
        }
      }

      val aboutText = recipient.combinedAboutAndEmoji
      if (aboutText.isNullOrEmpty()) {
        // TODO: probably should migrate to using newer pref store
        if (TextSecurePreferences.isAlsoShowProfileName(context)) {
          val displayName2 = recipient.getDisplayName2(context);
          if (!displayName2.isNullOrEmpty()) {
            about?.text = displayName2
            about?.visibility = View.VISIBLE
          } else {
            about?.visibility = View.GONE
          }
        } else {
          about?.visibility = View.GONE
        }
      } else {
        about?.text = recipient.combinedAboutAndEmoji
        about?.visibility = View.VISIBLE
      }
    }

    private fun observeRecipient(lifecycleOwner: LifecycleOwner?, recipient: Recipient?) {
      this.recipient?.live()?.liveData?.removeObserver(recipientObserver)

      this.recipient = recipient

      lifecycleOwner?.let {
        this.recipient?.live()?.liveData?.observe(lifecycleOwner, recipientObserver)
      }
    }

    private fun unbind() {
      observeRecipient(null, null)
    }
  }
}
