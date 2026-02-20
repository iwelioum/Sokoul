<?php global $user_login;
// In case of a login error.
if ( isset( $_GET['login'] ) && $_GET['login'] == 'failed') : ?>
		<div class="aa_error">
			<p><?php _z('FAILED: Try again!'); ?></p>
		</div>
<?php endif; ?>
	<header>
		<h1><?php _z('Log in'); ?></h1>
	</header>
	<?php get_template_part('pages/sections/login-form'); ?>

