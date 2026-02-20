<?php 

$links = (zeta_here_links($post->ID)) ? true : false;

$loggedin = (is_user_logged_in()) ? true : false;
if(isset($links) == true OR isset($loggedin) == true && ZetaLinks::front_publisher_role() === true){

$linksaccess = zeta_get_option('links_access');		
$access = ($linksaccess == true) ? ($loggedin) ? true : false : true;
	?>
	<div id="links" class="content-links">
		<div class="content-title">
		  <span class="title-head"><?php _z('Links'); ?></span>
		  <span class="title-sep <?php echo (isset($args['gall']) == true) ? null : 'non';?>"></span>
		</div>
		<?php if($links == true){?>
		<ul class="links-tabs">
				<?php // Menu Link types
				if(zeta_here_type_links($post->ID, __z('Download'))) echo '<li><a data-tabid="download">'. __z('Download'). '</a></li>';
				if(zeta_here_type_links($post->ID, __z('Torrent'))) echo '<li><a data-tabid="torrent">'. __z('Torrent'). '</a></li>';
				if(zeta_here_type_links($post->ID, __z('Watch online'))) echo '<li><a data-tabid="videos">'. __z('Watch'). '</a></li>';
				if(zeta_here_type_links($post->ID, __z('Rent or Buy'))) echo '<li><a data-tabid="buy">'. __z('Rent or Buy'). '</a></li>';
				// End Menu ?>
		</ul>
<?php //if($access == true){?>
		<div class="link-wrapper">
			<?php // Table lists

			ZetaLinks::tablelist_front($post->ID, __z('Download'), 'download');
			ZetaLinks::tablelist_front($post->ID, __z('Torrent'), 'torrent');
			ZetaLinks::tablelist_front($post->ID, __z('Watch online'), 'videos');
			ZetaLinks::tablelist_front($post->ID, __z('Rent or Buy'), 'buy');

		?>
		</div>
<?php //}?>
		<?php }?>
		<?php  if(is_user_logged_in() && ZetaLinks::front_publisher_role() === true) {?>
    <div id="form" class="add-links">
        <div id="result_link_form"></div>
        <div class="form_post_lik">
        	<form id="zetapostlinks" enctype="application/json" <?php echo ($links != true) ? 'class="active"' : null;?>>
        		<div class="table">
        			<table data-repeater-list="data" class="post_table" >
					<?php if($links != true){?>
        				<thead>
        					<tr>
        						<th><?php _z('Type'); ?></th>
        						<th><?php _z('URL'); ?></th>
        						<th><?php _z('Quality'); ?></th>
        						<th><?php _z('Language'); ?></th>
        						<th><?php _z('File size'); ?></th>
        						<th></th>
        					</tr>
        				</thead>
					<?php }?>
        				<tbody class="tbody">
        					<tr data-repeater-item class="row first_tr 00">
        						<td>
        							<select name="type">
        								<?php foreach( ZetaLinks::types() as $type) { echo "<option>{$type}</option>"; } ?>
        							</select>
        						</td>
        						<td>
        							<input name="url" type="text" class="url" placeholder="http://">
        						</td>
        						<td>
        							<select name="quality">
										<option selected disabled="disabled"><?php _z('Quality');?></option>
        							    <?php foreach( ZetaLinks::resolutions() as $resolution) { echo "<option>{$resolution}</option>"; } ?>
        							</select>
        						</td>
        						<td>
        							<select name="lang">
									<option selected disabled="disabled"><?php _z('Language');?></option>
        							    <?php foreach( ZetaLinks::langs() as $lang) { echo "<option>{$lang}</option>"; } ?>
        							</select>
        						</td>
        						<td>
        							<input name="size" type="text" class="size" placeholder="<?php _z('File size');?>">
        						</td>
        						<td>
        							<a data-repeater-delete class="remove_row">X</a>
        						</td>
        					</tr>

        				</tbody>
        			</table>
        		</div>
				<div class="add_links_new"><a class="add_links_toggle" <?php echo ($links != true) ? 'style="display:none;"' : null;?>><?php _z('Add Links');?></a></div>
        		<div class="control <?php echo ($links != true) ? 'active' : null;?>">					
        			<a data-repeater-create id="add_row" class="add_row">+ <?php _z('Add row'); ?></a>
        			<input type="submit" value="<?php _z('Send link(s)'); ?>" class="send_links">
        		</div>
        		<input type="hidden" name="post_id" value="<?php the_id(); ?>">
                <input type="hidden" name="nonce" value="<?php echo wp_create_nonce('zetalinks'); ?>">
                <input type="hidden" name="action" value="zetapostlinks">
        	</form>
        </div>
    </div>
		<?php }?>
	</div>	
<?php }?>













