# Project presentation references

Reviewed the public LubeLogger-Android, Goveon and TrailRelay README/release presentation on October 13, 2026.

- Lead with a short description of the app and its purpose; state Android requirements and independent-project status plainly.
- Put downloads near the top, linking to GitHub Releases rather than committing APKs.
- Use clickable screenshot tables with two columns, 260-pixel previews, readable alt text and short captions. Four screenshots use two rows, as in Goveon and TrailRelay.
- Keep the main README focused on features and getting started; put detailed behavior, signing and validation in linked guides.
- Record successful user use separately from automated verification and unresolved physical-device tests.
- Use local release signing and preserve the certificate for updates. Goveon explicitly documents its retained Android Debug-labelled certificate; Actual follows the owner's selected signing identity.
- Goveon uses MIT and TrailRelay uses Apache 2.0. Actual uses MIT for original wrapper source, with separate dependency notices.

Actual's screenshot order is budget, new transaction, accounts and reports. The four supplied files are included without editing, at the owner's request. Native configuration and diagnostics could be added to the gallery in a later release.
