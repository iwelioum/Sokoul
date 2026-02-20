<?php
/*
Template Name: ZT - Contact page
*/
get_header(); global $current_user; ?>
<div class="contact">
	<div class="contact-titl">
		<h1><?php _z('Contact Form'); ?></h1>
		<p class="descrip"><?php _z('Have something to notify our support team, fill the form and let\'s get it started.'); ?></p>
	</div>
	<div class="contact-body">
		<?php if(zeta_get_option('contact_form') == true) { ?>
		<form id="zetaflix-contact-form" class="contactame">
		<div class="form-group hlf">
            <?php if(!is_user_logged_in()){ ?>
				
					<fieldset class="nine">
						<input id="contact-name" type="text" name="name" placeholder="<?php _z('Name'); ?>" required>
					</fieldset>
					<fieldset class="nine fix">
						<input id="contact-email" type="text" name="email" placeholder="<?php _z('Email Address'); ?>" required>
					</fieldset>
				<?php } else { ?>
					<fieldset class="nine">
						<input id="contact-name" type="text" name="name" value="<?php echo $current_user->display_name; ?>" placeholder="<?php _z('Name'); ?>" required>
					</fieldset>
					<fieldset class="nine fix">
						<input id="contact-email" type="text" name="email" value="<?php echo $current_user->user_email; ?>" placeholder="<?php _z('Email Address'); ?>" required>
					</fieldset>
				
            <?php } ?>
			</div>
			<fieldset>
				<input id="contact-subject" type="text" name="subject" placeholder="<?php _z('Subject'); ?>"  required>
			</fieldset>
			<fieldset>
				<textarea id="contact-message" name="message" rows="5" cols="" placeholder="<?php _z('The more descriptive you can be the better we can help.'); ?>" required></textarea>
			</fieldset>
			<fieldset id="contact-response-message"></fieldset>
			<fieldset>
				<input id="contact-submit-button" type="submit" value="<?php _z('Send message'); ?>">
			</fieldset>
            <div id="zetaflix-reCAPTCHA-response"></div>
			<input type="hidden" name="action" value="omegadb_inboxes_form">
			<input type="hidden" name="type" value="contact">
		</form>
		<?php } else { ?>
		<fieldset id="contact-response-message">
			<div class="notice error animation-3"><?php _z('Contact form disabled'); ?></div>
		</fieldset>
		<?php } ?>
	</div>
</div>
<?php get_footer(); ?>
