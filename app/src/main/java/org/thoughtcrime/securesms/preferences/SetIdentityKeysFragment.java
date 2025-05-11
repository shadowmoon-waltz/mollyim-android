package org.thoughtcrime.securesms.preferences;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import org.thoughtcrime.securesms.R;

import org.signal.core.util.Base64;
import org.thoughtcrime.securesms.util.views.CircularProgressMaterialButton;
import org.signal.libsignal.protocol.IdentityKeyPair;
import org.thoughtcrime.securesms.keyvalue.SignalStore;

import android.widget.Button;

import android.view.Window;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricManager.Authenticators;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.navigation.Navigation;

public class SetIdentityKeysFragment extends Fragment {

  private EditText               publicKeyText;
  private EditText               privateKeyText;
  private CircularProgressMaterialButton applyButton;
  private Button                 populateButton;

  private char[]                 cachedPublicKey;
  private char[]                 cachedPrivateKey;

  private final int              authenticators = Authenticators.BIOMETRIC_STRONG | Authenticators.BIOMETRIC_WEAK | Authenticators.DEVICE_CREDENTIAL;
  private boolean                hadScreenLock = true;

  public static SetIdentityKeysFragment newInstance() {
    return new SetIdentityKeysFragment();
  }

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    TextSecurePreferences.setTempScreenSecurity(true);
    requireActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    return inflater.inflate(R.layout.set_identity_keys_fragment, container, false);
  }

  @Override
  public void onDestroyView() {
    TextSecurePreferences.setTempScreenSecurity(false);
    Window w = requireActivity().getWindow();
    if (w != null) {
      if (TextSecurePreferences.isScreenSecurityEnabled(requireContext())) {
        w.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
      } else {
        w.clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
      }
    }
    clearCachedKeys();
    super.onDestroyView();
  }

  // hide keys if necessary so that it won't show up on resume until user authenticates (best effort if available)
  // references: https://stackoverflow.com/questions/63806437 and https://developer.android.com/training/sign-in/biometric-auth
  @Override
  public void onResume() {
    if (BiometricManager.from(requireContext()).canAuthenticate(authenticators)
        == BiometricManager.BIOMETRIC_SUCCESS) {
      BiometricPrompt biometricPrompt = new BiometricPrompt(this, ContextCompat.getMainExecutor(requireContext()),
          new BiometricPrompt.AuthenticationCallback() {
        @Override
        public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
          super.onAuthenticationError(errorCode, errString);
          Navigation.findNavController(requireView()).popBackStack();
        }

        @Override
        public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
          super.onAuthenticationSucceeded(result);
          putBackCachedKeys();
        }

        @Override
        public void onAuthenticationFailed() {
          super.onAuthenticationFailed();
          Navigation.findNavController(requireView()).popBackStack();
        }
      });

      BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
          .setTitle("SignalSW")
          .setSubtitle("Authenticate to view/set identity keys")
          .setAllowedAuthenticators(authenticators)
          .build();

      biometricPrompt.authenticate(promptInfo);
    } else {
      if (hadScreenLock) {
        clearCachedKeys();
        hadScreenLock = false;
      } else {
        putBackCachedKeys();
      }
    }

    super.onResume();
  }

  @Override
  public void onPause() {
    hadScreenLock = (BiometricManager.from(requireContext()).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS);

    cachedPublicKey = publicKeyText.getText().toString().toCharArray();
    cachedPrivateKey = privateKeyText.getText().toString().toCharArray();
    publicKeyText.setText("");
    privateKeyText.setText("");

    super.onPause();
  }

  private void putBackCachedKeys() {
    if (cachedPublicKey != null) {
      publicKeyText.setText(cachedPublicKey, 0, cachedPublicKey.length);
    }
    if (cachedPrivateKey != null) {
      privateKeyText.setText(cachedPrivateKey, 0, cachedPrivateKey.length);
    }
    clearCachedKeys();
  }

  private void clearCachedKeys() {
    if (cachedPublicKey != null) {
      for (int i = 0; i < cachedPublicKey.length; i++) {
        cachedPublicKey[i] = 0;
      }
      cachedPublicKey = null;
    }
    if (cachedPrivateKey != null) {
      for (int i = 0; i < cachedPrivateKey.length; i++) {
        cachedPrivateKey[i] = 0;
      }
      cachedPrivateKey = null;
    }
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    this.publicKeyText  = view.findViewById(R.id.set_identity_keys_public_key);
    this.privateKeyText = view.findViewById(R.id.set_identity_keys_private_key);
    this.applyButton    = view.findViewById(R.id.set_identity_keys_apply);
    this.populateButton = view.findViewById(R.id.set_identity_keys_populate);

    applyButton.setOnClickListener(v -> onApplyClicked());

    populateButton.setOnClickListener(v -> onPopulateClicked());

    requireActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
  }

  private void onApplyClicked() {
    if (publicKeyText.getText().length() == 44 && privateKeyText.getText().length() == 44) {
      try {
        final byte[] publicKey = Base64.decode(publicKeyText.getText().toString());
        final byte[] privateKey = Base64.decode(privateKeyText.getText().toString());
        new AlertDialog.Builder(requireContext())
                 .setTitle("WARNING!")
                 .setMessage("Setting these keys to incorrect values may cause app issues. Continue?")
                 .setPositiveButton("Proceed", (d, i) -> {
                   d.dismiss();
                   SignalStore.account().setIdentityKeysManually(publicKey, privateKey);
                   new AlertDialog.Builder(requireContext())
                                  .setMessage("Identity keys set successfully.")
                                  .setPositiveButton(android.R.string.ok, (d2, i2) -> {
                                    d2.dismiss();
                                  })
                                  .show();
                 })
                 .setNegativeButton("Cancel", (d, i) -> {
                   d.dismiss();
                 })
                 .show();
        return;
      } catch (Exception e) {

      }
    }

    new AlertDialog.Builder(requireContext())
             .setMessage("Failed to set identity keys. Verify you entered the keys correctly (base 64, 44 characters).")
             .setPositiveButton(android.R.string.ok, (d, i) -> {
               d.dismiss();
             })
             .show();
  }

  private void onPopulateClicked() {
    new AlertDialog.Builder(requireContext())
             .setTitle("WARNING!")
             .setMessage("This will show public and private identity keys. Anyone that has them can potentially impersonate you on Signal. " +
                         "Only view somewhere relatively private, and be careful about it showing up in screenshots, recent apps, the clipboard, " +
                         "and wherever else you choose to store it.")
             .setPositiveButton("Proceed", (d, i) -> {
               try {
                 IdentityKeyPair ikp = SignalStore.account().getAciIdentityKey();
                 this.publicKeyText.setText(Base64.encodeWithPadding(ikp.getPublicKey().serialize()));
                 this.privateKeyText.setText(Base64.encodeWithPadding(ikp.getPrivateKey().serialize()));
               } catch (Exception e) {

               }
               d.dismiss();
             })
             .setNegativeButton("Cancel", (d, i) -> {
               d.dismiss();
             })
             .show();
  }
}
