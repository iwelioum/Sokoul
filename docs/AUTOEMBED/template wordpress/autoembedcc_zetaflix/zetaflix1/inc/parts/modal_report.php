<!-- Report Content -->
<?php if(zeta_get_option('report_form') == true) { ?>
            <div id="report-response-message"></div>
            <div class="zt-report-form">
                <form id="zt-report-form">
                    <fieldset>
                        <label>
                            <input class="report-video-checkbox" type="checkbox" name="problem[]" autocomplete="off" value="labeling">
                            <span class="title"><?php _z('Labeling problem'); ?></span>
                            <span class="text"><?php _z('Wrong title or summary, or episode out of order'); ?></span>
                        </label>
                        <label>
                            <input class="report-video-checkbox" type="checkbox" name="problem[]" autocomplete="off" value="video">
                            <span class="title"><?php _z('Video Problem'); ?></span>
                            <span class="text"><?php _z('Blurry, cuts out, or looks strange in some way'); ?></span>
                        </label>
                        <label>
                            <input class="report-video-checkbox" type="checkbox" name="problem[]" autocomplete="off" value="audio">
                            <span class="title"><?php _z('Sound Problem'); ?></span>
                            <span class="text"><?php _z('Hard to hear, not matched with video, or missing in some parts'); ?></span>
                        </label>
                        <label>
                            <input class="report-video-checkbox" type="checkbox" name="problem[]" autocomplete="off" value="caption">
                            <span class="title"><?php _z('Subtitles or captions problem'); ?></span>
                            <span class="text"><?php _z('Missing, hard to read, not matched with sound, misspellings, or poor translations'); ?></span>
                        </label>
                        <label>
                            <input class="report-video-checkbox" type="checkbox" name="problem[]" autocomplete="off" value="buffering">
                            <span class="title"><?php _z('Buffering or connection problem'); ?></span>
                            <span class="text"><?php _z('Frequent rebuffering, playback won\'t start, or other problem'); ?></span>
                        </label>
                    </fieldset>
                    <fieldset id="report-video-message-field">
                        <textarea name="message" rows="3" placeholder="<?php _z("What is the problem? Please explain.."); ?>"></textarea>
                    </fieldset>
                    <fieldset id="report-video-email-field">
                        <input type="email" name="email" placeholder="<?php _z('Email address'); ?>">
						<span class="field-desc mini">* Your email address will not be shared.</span>
                    </fieldset>
                    <fieldset id="report-video-button-field">
                        <input id="report-submit-button" type="submit" value="<?php _z('Send report'); ?>">
                        <input type="hidden" name="action" value="omegadb_inboxes_form">
            			<input type="hidden" name="type" value="report">
                        <input type="hidden" name="postid" value="<?php the_id(); ?>">
                        <input type="hidden" name ="nonce" value="<?php echo zetaflix_create_nonce('zetaflix_report_nonce'); ?>">
                    </fieldset>
                </form>
            </div>
<?php } ?>
