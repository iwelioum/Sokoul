<?php
/**
 * Displays header site login and registration
 *
 * @package fmovie
 */
// reccomended
$fmovie_login = get_option('admin_login');
?>
<?php if ($fmovie_login == 1) {  ?>
<?php if ( is_user_logged_in() ) { ?>
<div id="user">
	<div onclick="location.href='<?php echo wp_logout_url(); ?>';" class="guest">
		<i class="fa fa-user-circle"></i>
		<span><?php echo esc_html__( 'Logout', 'fmovie' ); ?></span>
	</div>
</div>
<?php } else { ?>
<div id="user">
	<div onclick="location.href='<?php echo esc_url( wp_login_url() ); ?>';" class="guest">
		<i class="fa fa-user-circle"></i>
		<span><?php echo esc_html__( 'Login / Register', 'fmovie' ); ?></span>
	</div>
</div>
<?php } ?>
<?php } ?>