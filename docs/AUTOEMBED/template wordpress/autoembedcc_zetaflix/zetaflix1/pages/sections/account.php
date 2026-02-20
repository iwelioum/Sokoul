<?php
/*
* -------------------------------------------------------------------------------------
* @author: Zetathemes
* @author URI: https://zetathemes.com/
* @copyright: (c) 2023 Zetathemes. All rights reserved
* -------------------------------------------------------------------------------------
*
* @since 1.0.0
*
*/

global $current_user, $wp_roles, $wpdb;
wp_get_current_user();
$user_id	= get_current_user_id();
$first_name = get_user_meta($user_id, 'first_name', true);
$last_name	= get_user_meta($user_id, 'last_name', true);
$about		= get_user_meta($user_id, 'description', true);
$avatar		= get_user_meta($user_id, 'zt_user_icon', true);
$list		= get_user_meta($user_id, $wpdb->prefix .'user_list_count', true);
$view		= get_user_meta($user_id, $wpdb->prefix .'user_view_count', true);
$facebook 		= get_user_meta($user_id, 'zt_facebook', true);
$twitter 		= get_user_meta($user_id, 'zt_twitter', true);


$avatar_src = zeta_get_option('avatar_source');

$display_name = $current_user->display_name;
$poster = zeta_get_option('poster_style');
$itemnum = ($poster === 'vertical') ? 18 : 16;

$account = zeta_get_option('pageaccount');

$display = zeta_get_option('pageaccount_display');
$account_sub = zeta_get_option('pageaccount_subpages');


$list_page = (!empty($account_sub['pageaccount_list'])) ? $account_sub['pageaccount_list'] : 'list';
$seen_page = (!empty($account_sub['pageaccount_seen'])) ? $account_sub['pageaccount_seen'] : 'seen';
$links_page = (!empty($account_sub['pageaccount_links'])) ? $account_sub['pageaccount_links'] : 'links';
$linksp_page = (!empty($account_sub['pageaccount_linkspending'])) ? $account_sub['pageaccount_linkspending'] : 'links-pending';
$settings_page = (!empty($account_sub['pageaccount_settings'])) ? $account_sub['pageaccount_settings'] : 'settings';
$avatar_page = (!empty($account_sub['pageaccount_avatar'])) ? $account_sub['pageaccount_settings'] : 'edit-icon';

$avatar_page = get_permalink($account).'?'.$avatar_page;


if($display != "single"){
	
	$list_url = get_permalink($account).'?'.$list_page;
	$seen_url = get_permalink($account).'?'.$seen_page;
	$links_url = get_permalink($account).'?'.$links_page;
	$linksp_url = get_permalink($account).'?'.$linksp_page;
	$settings_url = get_permalink($account).'?'.$settings_page;


	if(isset($_GET[$list_page])){
		$nonce = wp_create_nonce('zt-list-items'); 
	}
	if(isset($_GET[$seen_page])){
		$nonce = wp_create_nonce('zt-view-items'); 
	}
	
} else {
	
	$list_url = '#'.$list_page;
	$seen_url = '#'.$seen_page;
	$links_url = '#'.$links_page;
	$linksp_url = '#'.$linksp_page;
	$settings_url = '#'.$settings_page;
	
	
	$nonce_l = wp_create_nonce('zt-list-items'); 
	$nonce_s = wp_create_nonce('zt-view-items'); 
	
}

get_header(); 

?>

<div class="profile-page">
	<div class="profile-heading">
		<div class="profile-info">
			<div class="info-avatar">
				<a href="<?php echo apply_filters('account_icon_url', $avatar_page);?>" class="avatar-edit" title="<?php _z('Change Icon');?>"><i class="fa-solid fa-pencil"></i></a>
				<a href="<?php echo apply_filters('account_user_url', get_author_posts_url( $user_id )); ?>" class="avatar-img"><?php echo zeta_avatar_account($user_id); ?></a>
			</div>
			<div class="info-user">
				<div class="info-row">
					<span class="user-name"><a href="<?php echo get_author_posts_url( $user_id ); ?>"><?php echo $display_name; ?></a></span> 
					<a href="<?php echo apply_filters('account_settings_url', $settings_url);?>" class="user-edit-profile"><span class="user-edit"><?php _z('Edit Profile');?></span></a>
				</div>
				<div class="info-row data">
					<span class="user-join"><strong><?php _z('Joined Date');?>:</strong> <?php $date = get_the_author_meta( 'user_registered', $user_id ); $date = date( 'Y-m-d', strtotime($date)); echo $date;?></span>					
				</div>
				
			</div>
			<?php if($twitter || $facebook){?>
			<div class="info-social">
				<?php if($facebook){?><a href="<?php echo $facebook;?>"><span class="social-btn facebook"><i class="fa-brands fa-facebook-f"></i></span></a><?php }?>
				<?php if($twitter){?><a href="<?php echo $twitter;?>"><span class="social-btn twitter"><i class="fa-brands fa-twitter"></i></span></a><?php }?>
			</div>
			<?php }?>
		</div>
	</div>
	<div class="profile-activity">
		<div class="activity-col-mob">
			<div class="activity-col">
				<span class="activity-num"><?php echo ( $list >= 1 ) ? $list : '0'; ?></span>
				<span class="activity-titl"><?php _z('My List');?></span>
			</div>
			<div class="activity-col">
				<span class="activity-num"><?php echo ( $view >= 1 ) ? $view : '0'; ?></span>
				<span class="activity-titl"><?php _z('Seen List');?></span>
			</div>
		</div>
		<div class="activity-col-mob">
			<div class="activity-col">
				<span class="activity-num"><?php echo count_user_posts( $user_id, 'zt_links'); ?></span>
				<span class="activity-titl"><?php _z('Shared Links');?></span>
			</div>
			<div class="activity-col">
				<span class="activity-num"><?php $args = array('user_id' => $user_id,'count' => true); $comments = get_comments($args); echo $comments ?></span>
				<span class="activity-titl"><?php _z('Comments');?></span>
			</div>
		</div>
	</div>		
	<div class="profile-menu">

		<?php if(isset($_GET['edit-icon']) || isset($_GET['settings']) || isset($_GET['links-pending'])){?>
			<?php if(isset($_GET['links-pending'])){?>
				<a href="<?php echo apply_filters('account_links_url', $links_url);?>" class="back-to-profile"><i class="fa-solid fa-angles-left"></i> <?php _z('Back');?></a>			
			<?php }else{?>
				<a href="<?php echo apply_filters('account_page_url', get_permalink($account));?>" class="back-to-profile"><i class="fa-solid fa-angles-left"></i> <?php _z('Profile');?></a>
			<?php }?>
			<?php if(isset($_GET['edit-icon'])){?>
			<a href="#" class="active"><i class="fa-solid fa-pencil"></i> <?php _z('Edit Icon');?></a>
			<?php }?>
			<?php if(isset($_GET['links-pending'])){?>
			<a href="#" class="active"><i class="fa-solid fa-link"></i> <?php _z('Pending');?></a>
			<?php }?>
			<?php if(isset($_GET['settings'])){?>
			<a href="#" class="active"><i class="fa-solid fa-gear"></i> <?php _z('Edit Profile');?></a>
			<?php }?>
		<?php }else{?>
			<a href="<?php echo apply_filters('account_list_url', $list_url);?>" <?php echo (isset($_GET['list']) || empty($_GET)) ? 'class="active"' : null;?> data-profiletab="list"><i class="fa-regular fa-grid-2"></i> <?php _z('List');?></a>
			<a href="<?php echo apply_filters('account_seen_url', $seen_url);?>" <?php echo (isset($_GET['seen'])) ? 'class="active"' : null;?> data-profiletab="seen"><i class="fa-solid fa-eye"></i> Seen</a>
			<a href="<?php echo apply_filters('account_links_url', $links_url);?>" <?php echo (isset($_GET['links']) || isset($_GET['links-pending'])) ? 'class="active"' : null;?> data-profiletab="links"><i class="fa-solid fa-link"></i> <?php _z('Links');?></a>
			<?php if(!isset($_GET['settings'])) { ?><a href="<?php echo apply_filters('account_settings_url', $settings_url);?>" data-profiletab="links"><i class="fa-solid fa-gear"></i> <?php _z('Settings');?></a><?php }?>
		<?php }?>
	</div>


	
	<div class="site-message"></div>
	
		<div class="profile-pages">
			<div id="profile-list" class="page-display list <?php echo (isset($_GET['list']) || empty($_GET)) ? 'active' : null;?>">
				<div id="list-items">
				<?php zeta_collections_items($user_id, array('movies','tvshows','seasons'), $itemnum, '_zt_list_users', 'list'); ?>
				</div>
			<?php 
			if( $list >= $itemnum ) { ?>
				<div class="paged">
				<input type="button" class="load_more load_list_favorites" data-items="<?php echo $itemnum;?>" data-ptype="movies" data-user="<?php echo $user_id; ?>" data-type="_zt_list_users" data-template="list" data-btxt="<?php _z('Load more');?>" data-ltxt="<?php _z('Loading...');?>" value="<?php _z('Load more'); ?>" data-page="1">
				</div>
				<?php } ?>
			</div>
			<div id="profile-seen" class="page-display seen<?php echo (isset($_GET['seen'])) ? ' active' : null;?>">
			<div id="seen-items">
				<?php zeta_collections_items($user_id, array('movies','tvshows','seasons'), $itemnum, '_zt_views_users', 'seen'); ?>
				</div>
				<?php if( $view >= $itemnum ) { ?>
				<div class="paged">
				<input type="button" class="load_more load_list_views" data-items="<?php echo $itemnum;?>" data-ptype="movies" data-user="<?php echo $user_id; ?>" data-type="_zt_views_users" data-template="seen" data-btxt="<?php _z('Load more');?>" data-ltxt="<?php _z('Loading...');?>" value="<?php _z('Load more'); ?>" data-page="1">
				</div>
				<?php } ?>
			</div>
			<div id="profile-links" class="page-display links<?php echo (isset($_GET['links']) || isset($_GET['links-pending'])) ? ' active' : null;?>">
			  <?php if (current_user_can('administrator')) { $total = zeta_total_count('zt_links', 'pending'); if($total >= 1) { ?>
			  
				  <?php if(isset($_GET['links']) && !isset($_GET['links-pending']) || $display !== "multi"){ ?><span class="pending" style="<?php if(isset($_GET['links-pending'])) { echo 'display: none;'; }?>"><a href="<?php echo $linksp_url;?>"><?php _z('Pendings'); ?> <i><?php echo $total; ?></i></a></span>
			  <?php } }?>
				<?php  } ?>		
			<?php if(!isset($_GET['links-pending'])){ ?>
			  <div id="user-links">
					<table class="links-table">
					  <thead>
						<tr>
						  <th><?php _z('Source');?></th>
						  <th><?php _z('Title');?></th>
						  <th><?php _z('Quality');?></th>
						  <th><?php _z('Language');?></th>
						  <th><?php _z('Views');?></th>
						  <th><?php _z('Date');?></th>
						  <th><?php _z('Status');?></th>
						  <?php if(current_user_can('administrator')){?>
						  <th><?php _z('Manage');?></th>
						  <?php }?>
						</tr>
					  </thead>
					 <tbody id="item_links">
			  
	  
				<?php if(isset($_GET['links-pending'])){ ?><a <?php echo $links_l;?>><span class="pending" style=""><?php _z('View All'); ?></span></a><?php }?>
						<?php zeta_links_account($user_id, 10 ); ?>
					 </tbody>
					</table>
				</div>
					<div class="paged">
						<input type="button" class="load_more load_list_links" data-btxt="<?php _z('Load more');?>" data-ltxt="<?php _z('Loading...');?>"data-page="1" data-user="<?php echo $user_id; ?>" value="<?php _z('Load more'); ?>">
					</div>
			  <?php }?>
			  
				<?php if(isset($_GET['links-pending'])){?>
			  <div id="admin-links">
					<table class="links-table">
					  <thead>
						<tr>
							<th><?php _z('Server'); ?></th>
							<th><?php _z('Title'); ?></th>
							<th><?php _z('User'); ?></th>
							<th class="views"><?php _z('Clicks'); ?></th>
							<th class="status"><?php _z('Status'); ?></th>
							<th class="status"><?php _z('Manage'); ?></th>
						</tr>
					  </thead>
					 <tbody id="item_links_admin">
						<?php zeta_links_pending( 10 ); ?>
					 </tbody>
					</table>
				</div>
					<div class="paged">
						<input type="button" class="load_more load_admin_list_links" data-btxt="<?php _z('Load more');?>" data-ltxt="<?php _z('Loading...');?>"data-page="1" data-user="<?php echo $user_id; ?>" value="<?php _z('Load more'); ?>">
					</div>
				<?php }?>
			</div>
			<?php if(isset($_GET['edit-icon'])){
				$gallery = zeta_get_option('avatar-gallery');
				
			
				?>

			<div id="profile-icons" class="page-display icons<?php echo (isset($_GET['edit-icon'])) ? ' active' : null;?>">
				<form id="update_user_icon_page">
				<div class="icons-gallery">
					<div class="gallery-name default"><h3>Default</h3></div>
					<div class="gallery-images">					
						<label class="image-icon <?php echo ($avatar == 'default' || empty($avatar)) ? 'active' : null;?>">
							<img src="<?php echo get_template_directory_uri().'/assets/img/avatars/avatar_prev.svg'; ?>">
							<input type="radio" name="profile-icon" value="default">
							<span class="icon-border"></span>							
						</label>
					
					</div>
				</div>
				<?php if($gallery && is_array($gallery)){?>
				<?php foreach($gallery as $icons) {?>
				<div class="icons-gallery">
					<div class="gallery-name"><h3><span class="toggle" title="show"></span> <?php echo $icons['gallery-name'];?></h3></div>
					<div class="gallery-images" style="display: block;">
					<?php 
$icons = explode(',',$icons['gallery-images']);

foreach($icons as $icon){		

$img = wp_get_attachment_image_src( $icon, 'full' )

?>

<label class="image-icon <?php echo ($avatar === $icon) ? 'active' : null;?>">
							<img src="<?php echo $img[0];?>">
							<input type="radio" name="profile-icon" value="<?php echo $icon?>">
							<span class="icon-border"></span>							
						</label>
					
<?php }?>
					</div>
				</div>
				<?php }	?>	
				<?php }?>
				<div class="icons-buttons">
				<input id="updateusericon" type="submit" value="<?php _z('Save Changes');?>">
				</div>
				<?php wp_nonce_field('update-user-icon','update-user-icon-nonce')?>
				</form>
			</div>
			<?php }?>
			<div id="profile-settings" class="page-display settings<?php echo (isset($_GET['settings'])) ? ' active' : null;?>">

			
                
                <ul class="settings-menu">
                  <li><a data-profilesett="general" class="active"><?php _z('General');?></a></li>
                  <li><a data-profilesett="security" class=""><?php _z('Password');?></a></li>
                </ul>
                <form id="update_user_page" class="update_profile">
                  <div class="settings-tab general active">
				  <div class="fieldset-row">
                    <fieldset class="form-email">
                      <label for="email"><?php _z('E-mail');?></label>
                      <input type="text" id="email" name="email" value="<?php the_author_meta('user_email', $current_user->ID ); ?>">
                    </fieldset>
					
                    <fieldset class="form-display_name">
                      <label for="display_name"><?php _z('Display name publicly as');?></label>
					<select name="display_name" id="display_name"><br/>
						<?php if (!empty($current_user->first_name)): ?>
						<option <?php
						selected($display_name, $current_user->first_name); ?> value="<?php
						echo esc_attr($current_user->first_name); ?>"><?php
						echo esc_html($current_user->first_name); ?></option>
						<?php endif; ?>
						<option <?php selected($display_name, $current_user->user_nicename); ?> value="<?php
						echo esc_attr($current_user->user_nicename); ?>"><?php
						echo esc_html($current_user->user_nicename); ?></option>
						<?php if (!empty($current_user->last_name)): ?>
						<option <?php selected($display_name, $current_user->last_name); ?> value="<?php
						echo esc_attr($current_user->last_name); ?>"><?php
						echo esc_html($current_user->last_name); ?></option>
						<?php endif; ?>
						<?php if (!empty($current_user->first_name) && !empty($current_user->last_name)): ?>
						<option <?php selected($display_name, $current_user->first_name . ' ' . $current_user->last_name); ?> value="<?php
						echo esc_attr($current_user->first_name . ' ' . $current_user->last_name); ?>"><?php
						echo esc_html($current_user->first_name . ' ' . $current_user->last_name); ?></option>
						<option <?php selected($display_name, $current_user->last_name . ' ' . $current_user->first_name); ?> value="<?php
						echo esc_attr($current_user->last_name . ' ' . $current_user->first_name); ?>"><?php
						echo esc_html($current_user->last_name . ' ' . $current_user->first_name); ?></option>
						<?php endif; ?>
						</select>
                    </fieldset>
					</div>
                    <fieldset class="from-first-name min fix">
                      <label for="first-name"><?php _z('First Name');?></label>
                      <input type="text" id="first-name" name="first-name" value="<?php the_author_meta('first_name', $current_user->ID ); ?>">
                    </fieldset>
                    <fieldset class="form-last-name min">
                      <label for="last-name"><?php _z('Last Name');?></label>
                      <input type="text" id="last-name" name="last-name" value="<?php the_author_meta('last_name', $current_user->ID ); ?>">
                    </fieldset>
					<div class="fieldset-row">
                    <fieldset class="form-url-twitter">
                      <label for="facebook"><?php _z('Facebook Url');?></label>
                      <input type="text" id="facebook" name="facebook" value="<?php the_author_meta('zt_facebook', $current_user->ID ); ?>">
                    </fieldset>
                    <fieldset class="form-url-facebook">
                      <label for="twitter"><?php _z('Twitter Url');?></label>
                      <input type="text" id="twitter" name="twitter" value="<?php the_author_meta('zt_twitter', $current_user->ID ); ?>">
                    </fieldset>
					</div>
                  </div>
                  <div class="settings-tab about">
                    <fieldset class="form-description">
                      <label for="description"><?php _z('Description');?></label>
                      <textarea id="description" name="description" rows="3" cols=""><?php the_author_meta('description', $current_user->ID ); ?></textarea>
                    </fieldset>
                  </div>
                  <div class="settings-tab security">
                    <fieldset class="form-pass1 min fix">
                      <label for="pass1"><?php _z('New Password');?> *</label>
                      <input type="password" id="pass1" name="pass1">
                    </fieldset>
          
                    <fieldset class="form-pass2 min">
                      <label for="pass2"><?php _z('Repeat Password');?> *</label>
                      <input type="password" id="pass2" name="pass2">
                    </fieldset>
                  </div>
                  <fieldset class="form-submit">
                    <input name="updateuser" type="submit" id="updateuser" class="submit button" data-text="<?php _z('Save Changes'); ?>" value="Update account" >
                    <?php wp_nonce_field('update-user','update-user-nonce')?>
					</fieldset>
                </form>
                <div class="clearfix"></div>

			</div>
			 <div class="clearfix"></div>
		</div>
	</div>
      </div>


			<script type='text/javascript'>
			

			
			
				jQuery(document).ready(function($) {
					
					$('#update_user_icon_page').submit(function(){
						var txt = $('#updateusericon').data('text');
						$('#updateusericon').attr('disabled', true);
						$('.site-message div').remove();
						$('#message').html('<div class="sms"><div class="updating"><i class="icons-spinner9 loading"></i> '+ ztAjax.updating + '</div></div>');
						$.ajax({
							type:'POST',
							dataType: 'json',
							url:ztAjax.url + '?action=zt_update_user_icon',
							data:$(this).serialize(),
							success: function(data) {
								console.log(data);
								$('#updateusericon').attr("disabled", false);
								$('.site-message').append(data.msg);
								$('.avatar-img img').attr('src', data.img);
								$('.user-avatar img').attr('src', data.img);
								$('html, body').animate({scrollTop: $(".site-message").offset().top}, 300);
								
								
							}
						})

						return false;
					});
					
					
					$('#update_user_page').submit(function(){
						var txt = $('#updateuser').data('text');
						$('#updateuser').attr('disabled', true);
						$('#updateuser').val(ztAjax.updating);
						$('.site-message div').remove();
						
						$('#message').html('<div class="sms"><div class="updating"><i class="icons-spinner9 loading"></i> '+ ztAjax.updating + '</div></div>');
						$.ajax({
							type:'POST',
							url:ztAjax.url + '?action=zt_update_user',
							data:$(this).serialize()
						})
						.done(function(data){
							console.log(data);
							$('#updateuser').attr("disabled", false);
							$('#updateuser').val(txt);
							$('.site-message').append(data);
							$('html, body').animate({scrollTop: $(".site-message").offset().top}, 300);
						});
						return false;
					});
					
					
					
					
					$('#description').bind('change', function(){
						$('#puser').text($(this).val());
					});
					$('#display_name').bind('change', function(){
						$('#h2user').text($(this).val());
					});
				});
			</script>

<?php get_footer(); ?>
