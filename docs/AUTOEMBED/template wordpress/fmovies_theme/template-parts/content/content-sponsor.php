<?php
/**
 * Displays sponsors
 *
 * @package fmovie
 */

$fmovie_sponsor = get_option('admin_sponsor');

?>

<?php if ($fmovie_sponsor == 1) {  ?>
<style>.watch-extra .bl-1{float:left!important;width:65%!important}@media screen and (max-width: 1279px){.watch-extra .bl-1{float:none!important;width:100%!important}.watch-extra .bl-2{float:none!important;width:100%!important;margin:0}}</style>
	<div class="bl-2 mb-4 mt-3">
        <section class="bl">
			<a class="btn btn-primary btn-lg w-100 mb-3" href="<?php echo sponsor1; ?>" rel="nofollow" role="button"><?php echo button1; ?></a>
			<a class="btn btn-primary btn-lg w-100" href="<?php echo sponsor2; ?>" rel="nofollow" role="button"><?php echo button2; ?></a>
		</section>
	</div>
<?php } ?>
