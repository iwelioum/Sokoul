<?php


/* Exit if accessed directly */
if ( ! defined( 'ABSPATH' ) ) {
	exit;
}
$theme_wp = wp_get_theme();
?>
<style>
:root {

    --primary: #5b3adf !important;

}
</style>
<div class="admin-container">
	<div class="admin-column admin-column-sticky">
		<div class="admin-box">
			<div class="admin-logo">
			<a href="<?php echo esc_url( __( wp_get_theme()->get( 'AuthorURI' )) ); ?>"><img src="<?php echo esc_url( get_template_directory_uri() ); ?>/admin/images/fr0zen.png" alt="<?php echo esc_attr__('fr0zen', 'fmovie'); ?>" /></a>
			<br>
			<span><?php echo __('FMovies', 'fmovie'); ?> <p><?php echo wp_get_theme()->get( 'Version' ) ?></p></span>
			</div>
			<nav class="admin-navigation">
				<ul>
					<li><a href="<?php echo admin_url('admin.php?page=admin-main'); ?>" class="admin-icon-nav-10 <?php if ($_REQUEST['page'] == 'admin-main'): echo 'active'; endif; ?>"><?php echo __('General', 'fmovie'); ?></a></li>
					<li><a href="<?php echo admin_url('admin.php?page=admin-home'); ?>" class="admin-icon-nav-1 <?php if ($_REQUEST['page'] == 'admin-home'): echo 'active'; endif; ?>"><?php echo __('Home', 'fmovie'); ?></a></li>
					<li><a href="<?php echo admin_url('admin.php?page=admin-branding'); ?>" class="admin-icon-nav-2 <?php if ($_REQUEST['page'] == 'admin-branding'): echo 'active'; endif; ?>"><?php echo __('Branding', 'fmovie'); ?></a></li>
					<li><a href="<?php echo admin_url('admin.php?page=admin-translate'); ?>" class="admin-icon-nav-3 <?php if ($_REQUEST['page'] == 'admin-translate'): echo 'active'; endif; ?>"><?php echo __('Translate', 'fmovie'); ?></a></li>
					<!--<li><a href="<?php echo admin_url('admin.php?page=admin-socialnetworks'); ?>" class="admin-icon-nav-4 <?php if ($_REQUEST['page'] == 'admin-socialnetworks'): echo 'active'; endif; ?>"><?php echo __('Social', 'fmovie'); ?></a></li>-->
					<li><a href="<?php echo admin_url('admin.php?page=admin-comments'); ?>" class="admin-icon-comments-8 <?php if ($_REQUEST['page'] == 'admin-comments'): echo 'active'; endif; ?>"><?php echo __('Comments', 'fmovie'); ?></a></li>
					<li><a href="<?php echo admin_url('admin.php?page=admin-advertising'); ?>" class="admin-icon-nav-7 <?php if ($_REQUEST['page'] == 'admin-advertising'): echo 'active'; endif; ?>"><?php echo __('Advertising', 'fmovie'); ?></a></li>
					<li><a href="<?php echo admin_url('admin.php?page=admin-player'); ?>" class="admin-icon-nav-9 <?php if ($_REQUEST['page'] == 'admin-player'): echo 'active'; endif; ?>"><?php echo __('Player', 'fmovie'); ?></a></li>
					<li><a href="<?php echo admin_url('admin.php?page=admin-reset'); ?>" class="admin-icon-nav-5 <?php if ($_REQUEST['page'] == 'admin-reset'): echo 'active'; endif; ?>"><?php echo __('Reset', 'fmovie'); ?></a></li>
				</ul>	
			</nav>
		</div>
	</div>